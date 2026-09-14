package com.outpost.gateway.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.outpost.account.repository.AccountRepository;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.gateway.GatewayApiApplication;
import com.outpost.gateway.GatewayStaticDataFixtures;
import com.outpost.integration.psp.CreatePspOrderRequest;
import com.outpost.integration.psp.CreatePspOrderResult;
import com.outpost.integration.psp.PspClient;
import com.outpost.integration.psp.PspResultCodes;
import com.outpost.integration.psp.RefundPspOrderRequest;
import com.outpost.integration.psp.RefundPspOrderResult;
import com.outpost.integration.psp.UnknownPspResultException;
import com.outpost.payment.ShopperDetail;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.payment.refund.repository.RefundRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = GatewayApiApplication.class)
class OrderModificationServiceIntegrationTest {
  private static final long MERCHANT_ACCOUNT_ID = 300L;
  private static final long OTHER_MERCHANT_ACCOUNT_ID = 301L;
  private static final long PSP_ACCOUNT_ID = 302L;
  private static final String PSP_CODE = "MOD_PSP";
  private static final String PSP_REFERENCE = "41";
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_modification",
          "outpost_gateway_modification",
          "outpost_gateway_modification");

  @Autowired private OrderRepository orderRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private RefundRepository refundRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

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
          "INSERT INTO account (account_id, account_type_id, parent_account_id, code, name, "
              + "is_active, created_ts) "
              + "VALUES (299, 1, NULL, 'MOD_ROOT', 'Modification root', true, now()), "
              + "(300, 2, 299, 'MOD_MERCHANT', 'Modification merchant', true, now()), "
              + "(301, 2, 299, 'MOD_OTHER_MERCHANT', 'Other merchant', true, now()), "
              + "(302, 4, 299, 'MOD_PSP', 'Modification PSP', true, now())");
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
    registry.add("OUTPOST_LEDGER_GATEWAY_HMAC_SECRET", () -> "gateway-integration-test-key");
  }

  @Test
  void storesTheRefundThePspAcceptedAndReturnsItsReference() {
    Order order = insertOrder(MERCHANT_ACCOUNT_ID, "order-valid", "idem-valid", PSP_REFERENCE);
    RecordingPsp psp =
        new RecordingPsp(new RefundPspOrderResult(PSP_REFERENCE, "77", PspResultCodes.ACCEPTED));

    OrderModificationResult result =
        service(psp).modify(MERCHANT_ACCOUNT_ID, command("order-valid", "refund-idem-valid"));

    assertThat(result.refundReference()).startsWith("refund-");
    Map<String, Object> stored =
        jdbcTemplate.queryForMap(
            "SELECT order_id, original_reference, merchant_reference, idempotency_key, "
                + "psp_refund_reference FROM merchant_refund WHERE refund_reference = ?",
            result.refundReference());
    assertThat(stored)
        .containsEntry("order_id", order.getOrderId().orElseThrow())
        .containsEntry("original_reference", "order-valid")
        .containsEntry("merchant_reference", "merchant-refund-order-valid")
        .containsEntry("idempotency_key", "refund-idem-valid")
        .containsEntry("psp_refund_reference", "77");
    assertThat(psp.refundRequests)
        .containsExactly(
            new RefundPspOrderRequest(PSP_CODE, PSP_REFERENCE, result.refundReference()));
  }

  @Test
  void rejectsRefundThePspRejectedAndStoresNothing() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-rejected", "idem-rejected", PSP_REFERENCE);
    RecordingPsp psp =
        new RecordingPsp(new RefundPspOrderResult(PSP_REFERENCE, null, PspResultCodes.REJECTED));

    OrderModificationException failure =
        catchThrowableOfType(
            OrderModificationException.class,
            () ->
                service(psp)
                    .modify(
                        MERCHANT_ACCOUNT_ID, command("order-rejected", "refund-idem-rejected")));
    assertThat(failure.code()).isEqualTo(OrderModificationErrorCodes.REFUND_REJECTED);
    assertThat(refundCount("order-rejected")).isZero();
  }

  @Test
  void answersRetryableWhenThePspResultIsUnknownAndStoresNothing() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-lost", "idem-lost", PSP_REFERENCE);
    PspClient psp =
        new PspClient() {
          @Override
          public CreatePspOrderResult createOrder(CreatePspOrderRequest request) {
            throw new UnsupportedOperationException();
          }

          @Override
          public RefundPspOrderResult refund(RefundPspOrderRequest request) {
            throw new UnknownPspResultException(
                new IllegalStateException("the PSP did not answer"));
          }
        };

    OrderModificationException failure =
        catchThrowableOfType(
            OrderModificationException.class,
            () ->
                service(psp)
                    .modify(MERCHANT_ACCOUNT_ID, command("order-lost", "refund-idem-lost")));
    assertThat(failure.code()).isEqualTo(OrderModificationErrorCodes.PSP_RETRYABLE);
    assertThat(refundCount("order-lost")).isZero();
  }

  @Test
  void rejectsForeignOrder() {
    insertOrder(OTHER_MERCHANT_ACCOUNT_ID, "order-foreign", "idem-foreign", PSP_REFERENCE);
    RecordingPsp psp =
        new RecordingPsp(new RefundPspOrderResult(PSP_REFERENCE, "77", PspResultCodes.ACCEPTED));

    OrderModificationException failure =
        catchThrowableOfType(
            OrderModificationException.class,
            () ->
                service(psp)
                    .modify(MERCHANT_ACCOUNT_ID, command("order-foreign", "refund-idem-foreign")));
    assertThat(failure.code()).isEqualTo(OrderModificationErrorCodes.ORDER_NOT_FOUND);
    assertThat(psp.refundRequests).isEmpty();
  }

  @Test
  void rejectsAnOrderWithoutPspReference() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-unpaid", "idem-unpaid", null);
    RecordingPsp psp =
        new RecordingPsp(new RefundPspOrderResult(PSP_REFERENCE, "77", PspResultCodes.ACCEPTED));

    OrderModificationException failure =
        catchThrowableOfType(
            OrderModificationException.class,
            () ->
                service(psp)
                    .modify(MERCHANT_ACCOUNT_ID, command("order-unpaid", "refund-idem-unpaid")));
    assertThat(failure.code()).isEqualTo(OrderModificationErrorCodes.ORDER_NOT_PAID);
    assertThat(psp.refundRequests).isEmpty();
  }

  private OrderModificationService service(PspClient psp) {
    return new OrderModificationService(orderRepository, psp, refundRepository);
  }

  private static OrderModificationCommand command(String orderReference, String idempotencyKey) {
    return new OrderModificationCommand(
        orderReference,
        idempotencyKey,
        "merchant-refund-" + orderReference,
        OrderModificationTypes.REFUND);
  }

  private int refundCount(String orderReference) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM merchant_refund WHERE original_reference = ?",
            Integer.class,
            orderReference));
  }

  private Order insertOrder(
      long merchantAccountId,
      String orderReference,
      String idempotencyKey,
      @Nullable String pspReference) {
    ShopperDetail shopper =
        new ShopperDetail(
            null,
            orderReference + "@example.test",
            "Shopper " + orderReference,
            Countries.GERMANY.getValue(),
            null,
            null);
    Order order =
        new Order(
            null,
            orderReference,
            "merchant-ref-for-" + orderReference,
            accountRepository.findAccountById(merchantAccountId).orElseThrow(),
            null,
            Countries.GERMANY.getValue(),
            null,
            eur(100L),
            eur(19L),
            eur(119L),
            idempotencyKey,
            "fingerprint-" + orderReference,
            accountRepository.findAccountById(PSP_ACCOUNT_ID).orElseThrow(),
            null,
            null,
            null,
            List.of(
                item(orderReference + "-line-1", orderReference + "-merchant-line-1", 50L, 10L),
                item(orderReference + "-line-2", orderReference + "-merchant-line-2", 50L, 9L)));
    Order stored = orderRepository.insertOrder(shopper, order).orElseThrow();
    if (pspReference != null) {
      orderRepository.updateOrderPspReferenceAndPaymentLink(
          orderReference, pspReference, "https://pay.example/" + orderReference);
    }
    return stored;
  }

  private static OrderItem item(
      String orderLineReference, String merchantLineReference, long net, long tax) {
    return new OrderItem(
        null,
        ProductTypes.DIGITAL_GOODS.getValue(),
        orderLineReference,
        merchantLineReference,
        eur(net),
        eur(tax),
        new BigDecimal("0.1900"));
  }

  private static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }

  /** A PSP that answers every refund with one chosen result and records what it was asked. */
  private static final class RecordingPsp implements PspClient {
    private final RefundPspOrderResult refundResult;
    private final List<RefundPspOrderRequest> refundRequests = new ArrayList<>();

    private RecordingPsp(RefundPspOrderResult refundResult) {
      this.refundResult = refundResult;
    }

    @Override
    public CreatePspOrderResult createOrder(CreatePspOrderRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public RefundPspOrderResult refund(RefundPspOrderRequest request) {
      refundRequests.add(request);
      return refundResult;
    }
  }
}
