package com.outpost.worker.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.accounting.queue.AccountingRequest;
import com.outpost.accounting.queue.AccountingRequestLine;
import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.accounting.queue.AccountingRequestResults;
import com.outpost.accounting.queue.AccountingRequestStatuses;
import com.outpost.accounting.queue.AccountingRequestTypes;
import com.outpost.accounting.queue.SubmitAccountingRequestCommand;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.integration.psp.service.CancelRequest;
import com.outpost.integration.psp.service.CancelResult;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.CreateOrderResult;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.RefundRequest;
import com.outpost.integration.psp.service.RefundResult;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.PspEventStatuses;
import com.outpost.payment.common.Amount;
import com.outpost.payment.repository.PaymentOrderRepository;
import com.outpost.payment.repository.RefundItemRepository;
import com.outpost.worker.accounting.client.LedgerPaymentClient;
import com.outpost.worker.accounting.repository.LedgerTransactionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    classes = com.outpost.worker.OutpostWorkerApplication.class,
    properties = {
      "outpost.worker.psp.enabled=false",
      "outpost.worker.accounting.enabled=false",
      "outpost.worker.ledger.base-url=http://localhost:8081",
      "outpost.worker.ledger.hmac-secret=test-worker-key"
    })
class MerchantRefundWorkflowIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_worker_refund", "outpost_worker_refund", "outpost_worker_refund");
  private static long nextId = 1;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AccountingRequestQueue requests;
  @Autowired private LedgerTransactionRepository transactions;
  @Autowired private PaymentOrderRepository orders;
  @Autowired private RefundItemRepository refundItems;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

  private FakeLedgerPaymentClient ledger;
  private FakePspClient psp;

  @BeforeAll
  static void migrateAndSeed() {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    seedReferenceData(new JdbcTemplate(dataSource()));
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @BeforeEach
  void resetFakes() {
    ledger = new FakeLedgerPaymentClient();
    psp = new FakePspClient();
  }

  @Test
  void anUncapturedPaymentIsCancelledAtThePspAndNothingIsReserved() {
    String paymentReference = fixture("payment-uncaptured", 1000, 190, false);

    AccountingRequestResults result = apply(paymentReference, "refund-uncaptured", List.of());

    assertThat(psp.cancels)
        .containsExactly(new CancelRequest("DEMO_PSP", "psp-" + paymentReference));
    assertThat(ledger.reservations).isEmpty();
    assertThat(psp.refunds).isEmpty();
    assertThat(result).isEqualTo(AccountingRequestResults.SUCCESS);
  }

  @Test
  void capturedPaymentIsReservedRefundedAndAccepted() {
    String paymentReference = fixture("payment-captured", 1000, 190, true);

    AccountingRequestResults result = apply(paymentReference, "refund-captured", List.of());

    assertThat(ledger.reservations)
        .containsExactly(new Reservation(paymentReference, "refund-captured", 1000, 190, "EUR"));
    assertThat(refundItemRows("refund-captured")).containsExactly(new RefundItemRow(1000, 190));
    assertThat(psp.refunds)
        .containsExactly(
            new RefundRequest(
                "DEMO_PSP", "psp-" + paymentReference, "refund-captured", amount(1190)));
    assertThat(ledger.events)
        .containsExactly(new RecordedEvent(paymentReference, "refund-captured", "REFUND_ACCEPTED"));
    assertThat(result).isEqualTo(AccountingRequestResults.SUCCESS);
  }

  @Test
  void refundBeyondTheRefundableAmountIsRejectedAndThePspIsNeverCalled() {
    String paymentReference = fixture("payment-overrefunded", 1000, 190, true);
    long orderItemId = onlyOrderItemId(paymentReference);
    seedExistingRefund(paymentReference, "refund-existing", orderItemId, 1000, 190);

    AccountingRequestResults result = apply(paymentReference, "refund-over-limit", List.of());

    assertThat(ledger.reservations).isEmpty();
    assertThat(psp.refunds).isEmpty();
    assertThat(psp.cancels).isEmpty();
    assertThat(result).isEqualTo(AccountingRequestResults.FAILED);
  }

  @Test
  void pspFailureRecordsRefundFailedAndTheRequestEndsFailed() {
    String paymentReference = fixture("payment-psp-failure", 1000, 190, true);
    psp.nextRefundResult = ResultCode.REJECTED;

    AccountingRequestResults result = apply(paymentReference, "refund-psp-failure", List.of());

    assertThat(ledger.events)
        .containsExactly(
            new RecordedEvent(paymentReference, "refund-psp-failure", "REFUND_FAILED"));
    assertThat(result).isEqualTo(AccountingRequestResults.FAILED);
  }

  @Test
  void anUnknownPspOutcomeLeavesTheReservationForReconciliationWithoutRecordingFailure() {
    String paymentReference = fixture("payment-psp-unknown", 1000, 190, true);
    psp.nextRefundResult = ResultCode.UNKNOWN;

    AccountingRequestResults result = apply(paymentReference, "refund-psp-unknown", List.of());

    assertThat(ledger.reservations)
        .containsExactly(new Reservation(paymentReference, "refund-psp-unknown", 1000, 190, "EUR"));
    assertThat(ledger.events).isEmpty();
    assertThat(result).isEqualTo(AccountingRequestResults.FAILED);
  }

  private AccountingRequestResults apply(
      String paymentReference, String refundReference, List<AccountingRequestLine> lines) {
    requests.submit(
        new SubmitAccountingRequestCommand(
            AccountingRequestTypes.REFUND_REQUEST,
            refundReference,
            paymentReference,
            10,
            null,
            "idempotency-" + refundReference,
            "merchant-reference",
            null,
            null,
            null,
            null,
            lines));
    AccountingRequest request = requests.claimNext().orElseThrow();
    MerchantRefundWorkflow workflow =
        new MerchantRefundWorkflow(orders, refundItems, transactions, ledger, psp);
    AccountingRequestResults result = workflow.apply(request);
    requests.complete(request, result);
    return result;
  }

  /**
   * Creates an order, its single line, a payment, and its transaction; returns the payment
   * reference.
   */
  private String fixture(String slug, long netAmount, long taxAmount, boolean captured) {
    long orderId = nextId++;
    long shopperId = nextId++;
    long orderItemId = nextId++;
    String orderReference = slug + "-order";
    String paymentReference = slug;
    String orderLineReference = slug + "-line";
    long grossAmount = netAmount + taxAmount;

    jdbcTemplate.update(
        "INSERT INTO shopper_detail (shopper_id, email, full_name, country_id) VALUES (?, ?, ?, 1)",
        shopperId,
        slug + "@example.test",
        "Shopper");
    jdbcTemplate.update(
        "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, account_id, "
            + "account_type_id, shopper_id, currency_id, net_amount, tax_amount, gross_amount, "
            + "idempotency_key, created_ts) VALUES (?, ?, ?, 10, 2, ?, 3, ?, ?, ?, ?, ?)",
        orderId,
        orderReference,
        slug + "-merchant-ref",
        shopperId,
        netAmount,
        taxAmount,
        grossAmount,
        slug + "-idempotency",
        Timestamp.from(Instant.parse("2026-09-12T09:00:00Z")));
    jdbcTemplate.update(
        "INSERT INTO order_item (order_item_id, order_id, sequence, product_type_id, "
            + "order_line_reference, merchant_line_reference, net_amount, tax_amount, tax_rate) "
            + "VALUES (?, ?, 1, 2, ?, ?, ?, ?, 0.19)",
        orderItemId,
        orderId,
        orderLineReference,
        slug + "-merchant-line",
        netAmount,
        taxAmount);
    jdbcTemplate.update(
        "INSERT INTO order_payment (order_id, payment_reference, psp_account_id, "
            + "psp_account_type_id, psp_reference, shopper_country_id, created_ts) "
            + "VALUES (?, ?, 11, 4, ?, 1, ?)",
        orderId,
        paymentReference,
        "psp-" + paymentReference,
        Timestamp.from(Instant.parse("2026-09-12T09:00:01Z")));
    jdbcTemplate.update(
        "INSERT INTO transaction (transaction_type_id, account_id, reference, quantity, "
            + "currency_id, created_ts) VALUES (1, 10, ?, ?, 3, ?)",
        paymentReference,
        grossAmount,
        Timestamp.from(Instant.parse("2026-09-12T09:00:02Z")));
    if (captured) {
      seedCapturedEvent(transactionIdByReference(paymentReference), slug + "-capture", grossAmount);
    }
    return paymentReference;
  }

  /**
   * Creates a CAPTURE child transaction under the payment, matching the parent/child shape {@code
   * CaptureService} writes in production, and records its CAPTURED event with the balanced journal
   * entry the database's accounting-evidence triggers require.
   */
  private void seedCapturedEvent(
      long paymentTransactionId, String captureReference, long grossAmount) {
    jdbcTemplate.update(
        "INSERT INTO transaction (transaction_type_id, parent_transaction_id, account_id, "
            + "reference, quantity, currency_id, created_ts) VALUES (2, ?, 10, ?, ?, 3, ?)",
        paymentTransactionId,
        captureReference,
        grossAmount,
        Timestamp.from(Instant.parse("2026-09-12T09:00:03Z")));
    seedEventWithJournalEntry(transactionIdByReference(captureReference), 5, grossAmount);
  }

  /**
   * Records a transaction event of the given type with the balanced journal entry the database's
   * accounting-evidence triggers require of it, inside one transaction so the deferred coupling
   * check passes.
   */
  private void seedEventWithJournalEntry(long transactionId, long eventTypeId, long grossAmount) {
    org.springframework.transaction.support.TransactionTemplate transactionTemplate =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    transactionTemplate.executeWithoutResult(
        ignored -> {
          long eventId =
              Objects.requireNonNull(
                  jdbcTemplate.queryForObject(
                      "INSERT INTO transaction_event (transaction_id, transaction_event_type_id, "
                          + "event_ts) VALUES (?, ?, ?) RETURNING transaction_event_id",
                      Long.class,
                      transactionId,
                      eventTypeId,
                      Timestamp.from(Instant.parse("2026-09-12T09:00:03Z"))));
          long entryId =
              Objects.requireNonNull(
                  jdbcTemplate.queryForObject(
                      "INSERT INTO journal_entry (transaction_event_id, journal_entry_type_id, "
                          + "booked, posted) VALUES (?, 1, ?, ?) RETURNING journal_entry_id",
                      Long.class,
                      eventId,
                      Timestamp.from(Instant.parse("2026-09-12T09:00:03Z")),
                      Timestamp.from(Instant.parse("2026-09-12T09:00:03Z"))));
          jdbcTemplate.update(
              "INSERT INTO journal_entry_line "
                  + "(journal_entry_id, register_id, currency_id, quantity) "
                  + "VALUES (?, 1, 3, ?)",
              entryId,
              grossAmount);
          jdbcTemplate.update(
              "INSERT INTO journal_entry_line "
                  + "(journal_entry_id, register_id, currency_id, quantity) "
                  + "VALUES (?, 2, 3, ?)",
              entryId,
              -grossAmount);
        });
  }

  /** Seeds an existing, confirmed refund transaction that already consumes an order line. */
  private void seedExistingRefund(
      String paymentReference, String refundReference, long orderItemId, long net, long tax) {
    long paymentTransactionId = transactionIdByReference(paymentReference);
    jdbcTemplate.update(
        "INSERT INTO transaction (transaction_type_id, parent_transaction_id, account_id, "
            + "reference, quantity, currency_id, created_ts) VALUES (3, ?, 10, ?, ?, 3, ?)",
        paymentTransactionId,
        refundReference,
        net + tax,
        Timestamp.from(Instant.parse("2026-09-12T09:05:00Z")));
    long refundTransactionId = transactionIdByReference(refundReference);
    seedEventWithJournalEntry(refundTransactionId, 9, net + tax);
    jdbcTemplate.update(
        "INSERT INTO refund_item (refund_id, order_item_id, net_amount, tax_amount) "
            + "VALUES (?, ?, ?, ?)",
        refundTransactionId,
        orderItemId,
        net,
        tax);
  }

  private long transactionIdByReference(String reference) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT transaction_id FROM transaction WHERE reference = ?", Long.class, reference));
  }

  private long onlyOrderItemId(String paymentReference) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT oi.order_item_id FROM order_item oi "
                + "JOIN order_payment op ON op.order_id = oi.order_id "
                + "WHERE op.payment_reference = ?",
            Long.class,
            paymentReference));
  }

  private List<RefundItemRow> refundItemRows(String refundReference) {
    long refundTransactionId = transactionIdByReference(refundReference);
    return jdbcTemplate.query(
        "SELECT net_amount, tax_amount FROM refund_item WHERE refund_id = ?",
        (rs, rowNum) -> new RefundItemRow(rs.getLong("net_amount"), rs.getLong("tax_amount")),
        refundTransactionId);
  }

  private static Amount amount(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  private static DriverManagerDataSource dataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(DATABASE.getJdbcUrl());
    dataSource.setUsername(DATABASE.getUsername());
    dataSource.setPassword(DATABASE.getPassword());
    return dataSource;
  }

  private static void seedReferenceData(JdbcTemplate seed) {
    seed.update("INSERT INTO country VALUES (1, 'DE', 'Germany')");
    seed.update("INSERT INTO account_type VALUES (2, 'MERCHANT'), (4, 'PSP')");
    seed.update("INSERT INTO currency VALUES (3, 'EUR', 2)");
    seed.update(
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "VALUES (10, 2, 'merchant', 'Merchant', true, now()), "
            + "(11, 4, 'DEMO_PSP', 'Demo PSP', true, now())");
    seed.update("INSERT INTO product_type VALUES (1, 'DIGITAL_GOODS'), (2, 'PHYSICAL_GOODS')");
    seed.update("INSERT INTO register_type VALUES (1, 'MERCHANT_PAYABLE'), (2, 'PSP_RECEIVABLE')");
    seed.update(
        "INSERT INTO register (register_id, account_id, register_type_id) "
            + "VALUES (1, 10, 1), (2, 11, 2)");
    seed.update("INSERT INTO journal_entry_type VALUES (1, 'CAPTURE')");
    seed.update(
        "INSERT INTO transaction_type VALUES (1, 'PAYMENT'), (2, 'CAPTURE'), (3, 'REFUND')");
    seed.update(
        "INSERT INTO transaction_event_type VALUES "
            + "(1, 'ORDER_CREATED', true), (2, 'AUTHORISED', false), (3, 'REFUSED', true), "
            + "(4, 'CANCELLED', true), (5, 'CAPTURED', true), (6, 'CAPTURE_FAILED', true), "
            + "(7, 'REFUND_REQUESTED', false), (8, 'REFUND_ACCEPTED', false), "
            + "(9, 'REFUNDED', true), (10, 'REFUND_FAILED', false)");
    for (PspEventCodes code : PspEventCodes.values()) {
      seed.update(
          "INSERT INTO psp_event_code VALUES (?, ?)",
          code.getValue().getPspEventCodeId(),
          code.name());
    }
    for (PspEventStatuses status : PspEventStatuses.values()) {
      seed.update(
          "INSERT INTO psp_event_status VALUES (?, ?)",
          status.getValue().getPspEventStatusId(),
          status.name());
    }
    for (PspEventResults result : PspEventResults.values()) {
      seed.update(
          "INSERT INTO psp_event_result VALUES (?, ?)",
          result.getValue().getPspEventResultId(),
          result.name());
    }
    for (AccountingRequestTypes type : AccountingRequestTypes.values()) {
      seed.update(
          "INSERT INTO accounting_request_type VALUES (?, ?)",
          type.getValue().accountingRequestTypeId(),
          type.name());
    }
    for (AccountingRequestStatuses status : AccountingRequestStatuses.values()) {
      seed.update(
          "INSERT INTO accounting_request_status_type VALUES (?, ?)",
          status.getValue().accountingRequestStatusId(),
          status.name());
    }
    for (AccountingRequestResults result : AccountingRequestResults.values()) {
      seed.update(
          "INSERT INTO accounting_request_result_type VALUES (?, ?)",
          result.getValue().accountingRequestResultId(),
          result.name());
    }
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }

  private record RecordedEvent(
      String paymentReference, @Nullable String refundReference, String event) {}

  private record Reservation(
      String paymentReference,
      String refundReference,
      long netAmount,
      long taxAmount,
      String currency) {}

  private record RefundItemRow(long netAmount, long taxAmount) {}

  /**
   * Stands in for Ledger, whose reservation creates a real REFUND transaction in the shared schema.
   */
  private final class FakeLedgerPaymentClient implements LedgerPaymentClient {
    private final List<RecordedEvent> events = new ArrayList<>();
    private final List<Reservation> reservations = new ArrayList<>();

    @Override
    public void recordEvent(
        String paymentReference, @Nullable String refundReference, String event) {
      events.add(new RecordedEvent(paymentReference, refundReference, event));
    }

    @Override
    public void recordCapture(
        String paymentReference,
        String captureReference,
        boolean success,
        long amount,
        String currency) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void reserveRefund(
        String paymentReference,
        String refundReference,
        long netAmount,
        long taxAmount,
        String currency) {
      reservations.add(
          new Reservation(paymentReference, refundReference, netAmount, taxAmount, currency));
      long paymentTransactionId = transactionIdByReference(paymentReference);
      jdbcTemplate.update(
          "INSERT INTO transaction (transaction_type_id, parent_transaction_id, account_id, "
              + "reference, quantity, currency_id, created_ts) VALUES (3, ?, 10, ?, ?, 3, ?)",
          paymentTransactionId,
          refundReference,
          netAmount + taxAmount,
          Timestamp.from(Instant.parse("2026-09-12T09:05:00Z")));
    }
  }

  private static final class FakePspClient implements PspClient {
    private final List<CancelRequest> cancels = new ArrayList<>();
    private final List<RefundRequest> refunds = new ArrayList<>();
    private ResultCode nextRefundResult = ResultCode.ACCEPTED;

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public RefundResult refund(RefundRequest request) {
      refunds.add(request);
      return new RefundResult(request.pspReference(), request.refundReference(), nextRefundResult);
    }

    @Override
    public CancelResult cancel(CancelRequest request) {
      cancels.add(request);
      return new CancelResult(request.pspReference(), ResultCode.ACCEPTED);
    }
  }
}
