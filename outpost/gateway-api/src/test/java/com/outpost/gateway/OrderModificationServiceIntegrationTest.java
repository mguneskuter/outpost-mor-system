package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.gateway.order.repository.OrderRepository;
import com.outpost.gateway.order.repository.OrderRepository.Line;
import com.outpost.gateway.order.repository.OrderRepository.NewOrder;
import com.outpost.gateway.order.repository.OrderRepository.PersistedOrder;
import com.outpost.gateway.order.service.OrderModificationCommand;
import com.outpost.gateway.order.service.OrderModificationCommand.RefundLineCommand;
import com.outpost.gateway.order.service.OrderModificationException;
import com.outpost.gateway.order.service.OrderModificationResult;
import com.outpost.gateway.order.service.OrderModificationService;
import com.outpost.payment.common.ProductTypes;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
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
  private static final long PRODUCT_TYPE_ID =
      ProductTypes.DIGITAL_GOODS.getValue().getProductTypeId();
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_modification",
          "outpost_gateway_modification",
          "outpost_gateway_modification");

  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderModificationService service;
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
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (300, 2, 'MOD_MERCHANT', 'Modification merchant', true, now()), "
              + "(301, 2, 'MOD_OTHER_MERCHANT', 'Other merchant', true, now()), "
              + "(302, 4, 'MOD_PSP', 'Modification PSP', true, now())");
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
  void storesRefundRequestWithResolvedLinesAndReturnsReceived() {
    PersistedOrder order =
        insertOrder(MERCHANT_ACCOUNT_ID, "order-valid", "payment-valid", "idem-valid");

    OrderModificationResult result =
        service.request(
            MERCHANT_ACCOUNT_ID,
            new OrderModificationCommand(
                "order-valid",
                "refund-idem-valid",
                "merchant-ref-valid",
                "REFUND",
                List.of(
                    new RefundLineCommand(order.lines().get(0).orderLineReference(), null, 20L),
                    new RefundLineCommand(
                        null, order.lines().get(1).merchantLineReference(), null))));

    assertThat(result.status()).isEqualTo("RECEIVED");
    assertThat(result.refundReference()).isNotBlank();
    var stored =
        jdbcTemplate.queryForMap(
            "SELECT original_reference, merchant_reference, idempotency_key "
                + "FROM accounting_request_queue WHERE reference = ?",
            result.refundReference());
    assertThat(stored.get("original_reference")).isEqualTo("payment-valid");
    assertThat(stored.get("merchant_reference")).isEqualTo("merchant-ref-valid");
    assertThat(stored.get("idempotency_key")).isEqualTo("refund-idem-valid");
    List<String> lineReferences =
        jdbcTemplate.queryForList(
            "SELECT order_line_reference FROM accounting_request_queue_line "
                + "WHERE queue_id = "
                + "(SELECT queue_id FROM accounting_request_queue WHERE reference = ?) "
                + "ORDER BY order_line_reference",
            String.class,
            result.refundReference());
    assertThat(lineReferences)
        .containsExactlyInAnyOrder(
            order.lines().get(0).orderLineReference(), order.lines().get(1).orderLineReference());
  }

  @Test
  void replayWithSameIdempotencyKeyReturnsOriginalReferenceAndStoresNothingNew() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-replay", "payment-replay", "idem-replay");
    OrderModificationCommand command =
        new OrderModificationCommand(
            "order-replay", "refund-idem-replay", "merchant-ref-replay", "REFUND", List.of());

    OrderModificationResult first = service.request(MERCHANT_ACCOUNT_ID, command);
    OrderModificationResult replay = service.request(MERCHANT_ACCOUNT_ID, command);

    assertThat(replay.refundReference()).isEqualTo(first.refundReference());
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM accounting_request_queue WHERE account_id = ? "
                + "AND idempotency_key = ?",
            Integer.class,
            MERCHANT_ACCOUNT_ID,
            "refund-idem-replay");
    assertThat(count).isEqualTo(1);
  }

  @Test
  void rejectsForeignOrder() {
    insertOrder(OTHER_MERCHANT_ACCOUNT_ID, "order-foreign", "payment-foreign", "idem-foreign");

    assertThatThrownBy(
            () ->
                service.request(
                    MERCHANT_ACCOUNT_ID,
                    new OrderModificationCommand(
                        "order-foreign",
                        "refund-idem-foreign",
                        "merchant-ref-foreign",
                        "REFUND",
                        List.of())))
        .isInstanceOf(OrderModificationException.class)
        .extracting(exception -> ((OrderModificationException) exception).code())
        .isEqualTo("ORDER_NOT_FOUND");
  }

  @Test
  void rejectsUnknownOrderLine() {
    insertOrder(
        MERCHANT_ACCOUNT_ID, "order-unknown-line", "payment-unknown-line", "idem-unknown-line");

    assertThatThrownBy(
            () ->
                service.request(
                    MERCHANT_ACCOUNT_ID,
                    new OrderModificationCommand(
                        "order-unknown-line",
                        "refund-idem-unknown-line",
                        "merchant-ref-unknown-line",
                        "REFUND",
                        List.of(new RefundLineCommand("does-not-exist", null, null)))))
        .isInstanceOf(OrderModificationException.class)
        .extracting(exception -> ((OrderModificationException) exception).code())
        .isEqualTo("UNKNOWN_ORDER_LINE");
  }

  @Test
  void rejectsLineNamingBothReferences() {
    PersistedOrder order =
        insertOrder(MERCHANT_ACCOUNT_ID, "order-ambiguous", "payment-ambiguous", "idem-ambiguous");

    assertThatThrownBy(
            () ->
                service.request(
                    MERCHANT_ACCOUNT_ID,
                    new OrderModificationCommand(
                        "order-ambiguous",
                        "refund-idem-ambiguous",
                        "merchant-ref-ambiguous",
                        "REFUND",
                        List.of(
                            new RefundLineCommand(
                                order.lines().get(0).orderLineReference(),
                                order.lines().get(0).merchantLineReference(),
                                null)))))
        .isInstanceOf(OrderModificationException.class)
        .extracting(exception -> ((OrderModificationException) exception).code())
        .isEqualTo("AMBIGUOUS_LINE_REFERENCE");
  }

  private PersistedOrder insertOrder(
      long merchantAccountId,
      String orderReference,
      String paymentReference,
      String idempotencyKey) {
    NewOrder order =
        new NewOrder(
            orderReference,
            "merchant-ref-for-" + orderReference,
            merchantAccountId,
            0,
            Currencies.EUR.getValue().getCurrencyId(),
            100L,
            19L,
            119L,
            idempotencyKey,
            "fingerprint-" + orderReference,
            paymentReference,
            PSP_ACCOUNT_ID,
            Instant.parse("2026-09-12T00:00:00Z"),
            new OrderRepository.Shopper(
                orderReference + "@example.test",
                "Shopper " + orderReference,
                Countries.GERMANY.getValue().getCountryId(),
                null,
                null),
            List.of(
                new Line(
                    1,
                    PRODUCT_TYPE_ID,
                    orderReference + "-line-1",
                    orderReference + "-merchant-line-1",
                    50L,
                    10L,
                    "0.1900"),
                new Line(
                    2,
                    PRODUCT_TYPE_ID,
                    orderReference + "-line-2",
                    orderReference + "-merchant-line-2",
                    50L,
                    9L,
                    "0.1900")));
    return Objects.requireNonNull(orderRepository.insert(order));
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
}
