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
import com.outpost.integration.psp.RefundPspOrderLine;
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
  private static final BigDecimal RATE = new BigDecimal("0.1900");
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
  void refundsTheNamedLineAndAsksThePspForThatLinesGross() {
    Order order = insertOrder(MERCHANT_ACCOUNT_ID, "order-line", "idem-line", PSP_REFERENCE);
    RecordingPsp psp = acknowledgingPsp();

    OrderModificationResult result =
        service(psp)
            .modify(MERCHANT_ACCOUNT_ID, command("order-line", "refund-1", "order-line-line-1"));

    assertThat(result.refundReference()).startsWith("refund-");
    Map<String, Object> stored =
        jdbcTemplate.queryForMap(
            "SELECT order_id, original_reference, merchant_reference, idempotency_key, "
                + "psp_refund_reference FROM merchant_refund WHERE refund_reference = ?",
            result.refundReference());
    assertThat(stored)
        .containsEntry("order_id", order.getOrderId().orElseThrow())
        .containsEntry("original_reference", "order-line")
        .containsEntry("merchant_reference", "merchant-refund-order-line")
        .containsEntry("idempotency_key", "refund-1")
        .containsEntry("psp_refund_reference", "77");
    assertThat(claimedLines("order-line")).containsExactly("order-line-line-1");
    assertThat(psp.refundRequests)
        .containsExactly(
            new RefundPspOrderRequest(
                PSP_CODE,
                PSP_REFERENCE,
                "order-line",
                result.refundReference(),
                eur(60L),
                List.of(new RefundPspOrderLine("order-line-line-1", RATE, eur(50L), eur(60L)))));
  }

  @Test
  void refundWithoutNamedLinesClaimsOnlyTheLinesNotYetRefunded() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-rest", "idem-rest", PSP_REFERENCE);
    service(acknowledgingPsp())
        .modify(MERCHANT_ACCOUNT_ID, command("order-rest", "refund-1", "order-rest-line-1"));
    RecordingPsp psp = acknowledgingPsp();

    OrderModificationResult result =
        service(psp).modify(MERCHANT_ACCOUNT_ID, command("order-rest", "refund-2"));

    assertThat(claimedLines("order-rest"))
        .containsExactly("order-rest-line-1", "order-rest-line-2");
    assertThat(psp.refundRequests)
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.refundReference()).isEqualTo(result.refundReference());
              assertThat(request.amount()).isEqualTo(eur(59L));
              assertThat(request.lines())
                  .extracting(RefundPspOrderLine::orderLineReference)
                  .containsExactly("order-rest-line-2");
            });
  }

  @Test
  void refusesNamedLineThatIsAlreadyRefundedWithoutCallingThePsp() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-claimed", "idem-claimed", PSP_REFERENCE);
    service(acknowledgingPsp())
        .modify(MERCHANT_ACCOUNT_ID, command("order-claimed", "refund-1", "order-claimed-line-1"));
    RecordingPsp psp = acknowledgingPsp();

    OrderModificationException failure =
        refusal(psp, command("order-claimed", "refund-2", "order-claimed-line-1"));

    assertThat(failure.code()).isEqualTo(OrderModificationErrorCodes.ORDER_LINE_ALREADY_REFUNDED);
    assertThat(psp.refundRequests).isEmpty();
    assertThat(refundCount("order-claimed")).isEqualTo(1);
  }

  @Test
  void refusesRefundingAnOrderWhoseEveryLineIsRefundedWithoutCallingThePsp() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-done", "idem-done", PSP_REFERENCE);
    service(acknowledgingPsp()).modify(MERCHANT_ACCOUNT_ID, command("order-done", "refund-1"));
    RecordingPsp psp = acknowledgingPsp();

    OrderModificationException failure = refusal(psp, command("order-done", "refund-2"));

    assertThat(failure.code()).isEqualTo(OrderModificationErrorCodes.ORDER_ALREADY_REFUNDED);
    assertThat(psp.refundRequests).isEmpty();
  }

  @Test
  void refusesLineOfAnotherOrderRepeatedLineAndForeignOrderWithoutCallingThePsp() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-mine", "idem-mine", PSP_REFERENCE);
    insertOrder(MERCHANT_ACCOUNT_ID, "order-other", "idem-other", PSP_REFERENCE);
    insertOrder(OTHER_MERCHANT_ACCOUNT_ID, "order-foreign", "idem-foreign", PSP_REFERENCE);
    RecordingPsp psp = acknowledgingPsp();

    assertThat(refusal(psp, command("order-mine", "refund-1", "order-other-line-1")).code())
        .isEqualTo(OrderModificationErrorCodes.ORDER_LINE_NOT_FOUND);
    assertThat(
            refusal(
                    psp,
                    command("order-mine", "refund-2", "order-mine-line-1", "order-mine-line-1"))
                .code())
        .isEqualTo(OrderModificationErrorCodes.DUPLICATE_ORDER_LINE_REFERENCE);
    assertThat(refusal(psp, command("order-foreign", "refund-3")).code())
        .isEqualTo(OrderModificationErrorCodes.ORDER_NOT_FOUND);
    assertThat(psp.refundRequests).isEmpty();
    assertThat(refundCount("order-mine")).isZero();
  }

  @Test
  void releasesTheLinesOfRefundThePspRejected() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-rejected", "idem-rejected", PSP_REFERENCE);
    RecordingPsp rejecting =
        new RecordingPsp(new RefundPspOrderResult(PSP_REFERENCE, null, PspResultCodes.REJECTED));

    OrderModificationException failure =
        refusal(rejecting, command("order-rejected", "refund-1", "order-rejected-line-1"));

    assertThat(failure.code()).isEqualTo(OrderModificationErrorCodes.REFUND_REJECTED);
    assertThat(claimedLines("order-rejected")).isEmpty();
    OrderModificationResult retried =
        service(acknowledgingPsp())
            .modify(
                MERCHANT_ACCOUNT_ID,
                command("order-rejected", "refund-2", "order-rejected-line-1"));
    assertThat(retried.refundReference()).startsWith("refund-");
    assertThat(claimedLines("order-rejected")).containsExactly("order-rejected-line-1");
  }

  @Test
  void keepsTheLinesClaimedWhenThePspResultIsUnknown() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-lost", "idem-lost", PSP_REFERENCE);
    PspClient lost =
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

    OrderModificationException failure = refusal(lost, command("order-lost", "refund-1"));

    assertThat(failure.code()).isEqualTo(OrderModificationErrorCodes.PSP_RETRYABLE);
    assertThat(claimedLines("order-lost"))
        .containsExactly("order-lost-line-1", "order-lost-line-2");
    RecordingPsp psp = acknowledgingPsp();
    assertThat(refusal(psp, command("order-lost", "refund-1")).code())
        .isEqualTo(OrderModificationErrorCodes.ORDER_ALREADY_REFUNDED);
    assertThat(refusal(psp, command("order-lost", "refund-1", "order-lost-line-1")).code())
        .isEqualTo(OrderModificationErrorCodes.ORDER_LINE_ALREADY_REFUNDED);
    assertThat(psp.refundRequests).isEmpty();
  }

  @Test
  void rejectsAnOrderWithoutPspReference() {
    insertOrder(MERCHANT_ACCOUNT_ID, "order-unpaid", "idem-unpaid", null);
    RecordingPsp psp = acknowledgingPsp();

    OrderModificationException failure = refusal(psp, command("order-unpaid", "refund-1"));

    assertThat(failure.code()).isEqualTo(OrderModificationErrorCodes.ORDER_NOT_PAID);
    assertThat(psp.refundRequests).isEmpty();
  }

  private OrderModificationException refusal(PspClient psp, OrderModificationCommand command) {
    return catchThrowableOfType(
        OrderModificationException.class, () -> service(psp).modify(MERCHANT_ACCOUNT_ID, command));
  }

  private OrderModificationService service(PspClient psp) {
    return new OrderModificationService(orderRepository, psp, refundRepository);
  }

  private static RecordingPsp acknowledgingPsp() {
    return new RecordingPsp(new RefundPspOrderResult(PSP_REFERENCE, "77", PspResultCodes.ACCEPTED));
  }

  private static OrderModificationCommand command(
      String orderReference, String idempotencyKey, String... orderLineReferences) {
    return new OrderModificationCommand(
        orderReference,
        idempotencyKey,
        "merchant-refund-" + orderReference,
        OrderModificationTypes.REFUND,
        List.of(orderLineReferences));
  }

  private int refundCount(String orderReference) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM merchant_refund WHERE original_reference = ?",
            Integer.class,
            orderReference));
  }

  /** The order's lines with a refund that has not failed, in line order. */
  private List<String> claimedLines(String orderReference) {
    return jdbcTemplate.queryForList(
        "SELECT item.order_line_reference FROM refund_item "
            + "JOIN order_item item USING (order_item_id) "
            + "JOIN merchant_order o ON o.order_id = item.order_id "
            + "WHERE o.order_reference = ? AND NOT refund_item.refund_failed "
            + "ORDER BY item.order_item_id",
        String.class,
        orderReference);
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
        RATE);
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
