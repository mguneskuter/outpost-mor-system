package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.gateway.order.client.LedgerPayment;
import com.outpost.gateway.order.repository.OrderRepository;
import com.outpost.gateway.order.repository.OrderRepository.Line;
import com.outpost.gateway.order.repository.OrderRepository.NewOrder;
import com.outpost.gateway.order.repository.mybatis.MyBatisOrderRepository;
import com.outpost.gateway.order.repository.mybatis.OrderMapper;
import com.outpost.gateway.order.service.CreateOrderCommand;
import com.outpost.gateway.order.service.CreateOrderResult;
import com.outpost.gateway.order.service.OrderPhases;
import com.outpost.gateway.order.service.OrderService;
import com.outpost.integration.psp.service.CancelRequest;
import com.outpost.integration.psp.service.CancelResult;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.RefundRequest;
import com.outpost.integration.psp.service.RefundResult;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.tax.provider.TaxRateProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = GatewayApiApplication.class)
class MyBatisOrderRepositoryIntegrationTest {
  private static final long MERCHANT_ACCOUNT_ID = 100L;
  private static final long PSP_ACCOUNT_ID = 101L;
  private static final String PAYMENT_REFERENCE = "payment-lock-recovery";
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_order", "outpost_gateway_order", "outpost_gateway_order");

  @Autowired private OrderMapper mapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired
  @Qualifier("orderPhaseOwnershipDataSource")
  private DataSource phaseOwnershipDataSource;

  @BeforeAll
  static void migrateAndSeed() throws SQLException {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .schemas("public")
        .defaultSchema("public")
        .load()
        .migrate();
    JdbcTemplate seed =
        new JdbcTemplate(
            DataSourceBuilder.create()
                .url(DATABASE.getJdbcUrl())
                .username(DATABASE.getUsername())
                .password(DATABASE.getPassword())
                .build());
    GatewayStaticDataFixtures.materializeAll(seed);
    try (Connection connection = DATABASE.createConnection("")) {
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (100, 2, 'LOCK_MERCHANT', 'Lock merchant', true, now()), "
              + "(101, 4, 'LOCK_PSP', 'Lock PSP', true, now())");
      execute(
          connection, "INSERT INTO merchant_psp (account_id, psp_account_id) VALUES (100, 101)");
      execute(
          connection,
          "INSERT INTO merchant_fee_configuration "
              + "(account_id, account_type_id, currency_id, fee_mode_id, fee_rate_bps) "
              + "VALUES (100, 2, 1, 1, 0)");
    }
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
    registry.add(
        "OUTPOST_HMAC_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    registry.add("OUTPOST_OPERATOR_API_KEY", () -> "integration-operator-key");
  }

  @Test
  void releasesPostgresClaimAfterOwnershipSessionTerminates() throws Exception {
    OrderRepository firstRepository = new MyBatisOrderRepository(mapper, phaseOwnershipDataSource);
    OrderRepository secondRepository = new MyBatisOrderRepository(mapper, phaseOwnershipDataSource);
    PersistedOrderHandle persisted = insertOrder(firstRepository);
    UUID firstClaim = UUID.randomUUID();
    assertThat(
            firstRepository.claimPhase(
                persisted.orderId(), OrderPhases.ORDER_PERSISTED, firstClaim))
        .isTrue();
    assertThat(ownershipSettings()).containsExactly(10, 5, 3);

    CountDownLatch remoteCallStarted = new CountDownLatch(1);
    CountDownLatch releaseRemoteCall = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      final Future<?> remoteCall =
          executor.submit(
              () -> {
                remoteCallStarted.countDown();
                await(releaseRemoteCall);
              });
      assertThat(remoteCallStarted.await(5, TimeUnit.SECONDS)).isTrue();

      UUID secondClaim = UUID.randomUUID();
      assertThat(
              secondRepository.claimPhase(
                  persisted.orderId(), OrderPhases.ORDER_PERSISTED, secondClaim))
          .isFalse();

      terminateOwnershipSession();
      releaseRemoteCall.countDown();
      remoteCall.get(5, TimeUnit.SECONDS);

      RecordingLedger ledger = new RecordingLedger();
      OrderService service =
          new OrderService(
              secondRepository,
              ledger,
              new AcceptingPsp(),
              constantTaxRate(),
              Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC));

      CreateOrderResult result = service.create(MERCHANT_ACCOUNT_ID, command());

      assertThat(result.paymentLink()).isEqualTo("https://pay.example/recovered");
      assertThat(Objects.requireNonNull(ledger.payment).paymentReference())
          .isEqualTo(PAYMENT_REFERENCE);
      var completed =
          Objects.requireNonNull(
              secondRepository.findByIdempotency(MERCHANT_ACCOUNT_ID, "lock-recovery-key"));
      assertThat(completed.phase()).isEqualTo(OrderPhases.COMPLETED);
    }
  }

  private PersistedOrderHandle insertOrder(OrderRepository repository) {
    var order =
        new NewOrder(
            "order-lock-recovery",
            "merchant-order-lock-recovery",
            MERCHANT_ACCOUNT_ID,
            0,
            Currencies.EUR.getValue().getCurrencyId(),
            100L,
            19,
            119,
            "lock-recovery-key",
            fingerprint(command()),
            PAYMENT_REFERENCE,
            PSP_ACCOUNT_ID,
            Instant.parse("2026-09-12T00:00:00Z"),
            new OrderRepository.Shopper(
                "lock-recovery@example.test",
                "Lock Recovery Shopper",
                Countries.GERMANY.getValue().getCountryId(),
                null,
                null),
            java.util.List.of(
                new Line(
                    1,
                    1,
                    "order-line-lock-recovery",
                    "merchant-line-lock-recovery",
                    100,
                    19,
                    "0.1900")));
    var persisted = Objects.requireNonNull(repository.insert(order));
    return new PersistedOrderHandle(persisted.orderId());
  }

  private CreateOrderCommand command() {
    return new CreateOrderCommand(
        "merchant-order-lock-recovery",
        "lock-recovery-key",
        new CreateOrderCommand.ShopperDetailsCommand(
            "Lock Recovery Shopper", "lock-recovery@example.test", "DE", null, null),
        "LOCK_PSP",
        new CreateOrderCommand.OrderDetailsCommand(
            java.util.List.of(
                new CreateOrderCommand.OrderLineCommand(
                    "merchant-line-lock-recovery", 100L, "EUR", "DIGITAL_GOODS")),
            100L,
            "EUR"));
  }

  private static String fingerprint(CreateOrderCommand command) {
    StringBuilder canonical = new StringBuilder();
    append(canonical, command.merchantReference());
    append(canonical, command.idempotencyKey());
    append(canonical, command.paymentMethod());
    var shopper = Objects.requireNonNull(command.shopperDetails());
    append(canonical, shopper.fullName());
    append(canonical, shopper.email());
    append(canonical, shopper.country());
    append(canonical, shopper.state());
    append(canonical, shopper.zipcode());
    var details = Objects.requireNonNull(command.orderDetails());
    append(canonical, details.totalAmount());
    append(canonical, details.currency());
    var lines = Objects.requireNonNull(details.orderLines());
    append(canonical, lines.size());
    for (var line : lines) {
      var input = Objects.requireNonNull(line);
      append(canonical, input.merchantLineReference());
      append(canonical, input.amount());
      append(canonical, input.currency());
      append(canonical, input.type());
    }
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new AssertionError("SHA-256 is unavailable", exception);
    }
  }

  private static void append(StringBuilder target, @Nullable Object value) {
    String text = value == null ? "<null>" : value.toString();
    target.append(text.length()).append(':').append(text).append('|');
  }

  private int[] ownershipSettings() throws SQLException {
    try (Connection connection = phaseOwnershipDataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT current_setting('tcp_keepalives_idle')::int, "
                    + "current_setting('tcp_keepalives_interval')::int, "
                    + "current_setting('tcp_keepalives_count')::int");
        ResultSet result = statement.executeQuery()) {
      assertThat(result.next()).isTrue();
      return new int[] {result.getInt(1), result.getInt(2), result.getInt(3)};
    }
  }

  private void terminateOwnershipSession() {
    Integer pid =
        jdbcTemplate.queryForObject(
            "SELECT pid FROM pg_stat_activity "
                + "WHERE application_name = 'outpost-gateway-order-phase' "
                + "AND pid <> pg_backend_pid() "
                + "ORDER BY backend_start DESC LIMIT 1",
            Integer.class);
    assertThat(pid).isNotNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT pg_terminate_backend(?)", Boolean.class, Objects.requireNonNull(pid)))
        .isTrue();
  }

  private static TaxRateProvider constantTaxRate() {
    return (country, subdivision, productType, asOf) ->
        new com.outpost.tax.TaxRate(country, subdivision, new java.math.BigDecimal("0.19"));
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("timed out waiting for simulated remote call");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted while waiting for simulated remote call", exception);
    }
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }

  private record PersistedOrderHandle(long orderId) {}

  private static final class RecordingLedger
      implements com.outpost.gateway.order.client.LedgerClient {
    private @Nullable LedgerPayment payment;

    @Override
    public void createPayment(LedgerPayment payment) {
      this.payment = payment;
    }
  }

  private static final class AcceptingPsp implements PspClient {
    @Override
    public com.outpost.integration.psp.service.CreateOrderResult createOrder(
        CreateOrderRequest request) {
      return new com.outpost.integration.psp.service.CreateOrderResult(
          "psp-recovered", "https://pay.example/recovered", ResultCode.ACCEPTED);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CancelResult cancel(CancelRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
