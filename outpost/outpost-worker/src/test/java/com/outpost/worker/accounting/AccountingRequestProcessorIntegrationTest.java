package com.outpost.worker.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.outpost.accounting.queue.AccountingRequestLine;
import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.accounting.queue.AccountingRequestResults;
import com.outpost.accounting.queue.AccountingRequestStatuses;
import com.outpost.accounting.queue.AccountingRequestTypes;
import com.outpost.accounting.queue.SubmitAccountingRequestCommand;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.PspEventStatuses;
import com.outpost.worker.accounting.client.LedgerPaymentClient;
import com.outpost.worker.accounting.client.LedgerPaymentClientException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
class AccountingRequestProcessorIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_worker_accounting", "outpost_worker_accounting", "outpost_worker_accounting");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AccountingRequestQueue requests;

  private final FakeLedgerPaymentClient ledger = new FakeLedgerPaymentClient();

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
  void resetFake() {
    ledger.reset();
  }

  @Test
  void authorisationResultRecordsAuthorisedAndEndsSuccess() {
    seedPaymentTransaction("payment-auth-ok");
    submit(
        AccountingRequestTypes.AUTHORISATION_RESULT,
        "payment-auth-ok",
        "payment-auth-ok",
        true,
        null,
        null);

    processor().processNext();

    assertThat(ledger.events)
        .containsExactly(new RecordedEvent("payment-auth-ok", null, "AUTHORISED"));
    assertThat(resultOf("payment-auth-ok")).isEqualTo(AccountingRequestResults.SUCCESS);
  }

  @Test
  void authorisationResultRecordsRefusedForFailedAuthorisation() {
    seedPaymentTransaction("payment-auth-refused");
    submit(
        AccountingRequestTypes.AUTHORISATION_RESULT,
        "payment-auth-refused",
        "payment-auth-refused",
        false,
        null,
        null);

    processor().processNext();

    assertThat(ledger.events)
        .containsExactly(new RecordedEvent("payment-auth-refused", null, "REFUSED"));
    assertThat(resultOf("payment-auth-refused")).isEqualTo(AccountingRequestResults.SUCCESS);
  }

  @Test
  void captureResultRecordsCaptureWithAmountAndCurrency() {
    seedPaymentTransaction("payment-capture");
    submit(AccountingRequestTypes.CAPTURE_RESULT, "capture-1", "payment-capture", true, 1250L, 3L);

    processor().processNext();

    assertThat(ledger.captures)
        .containsExactly(new RecordedCapture("payment-capture", "capture-1", true, 1250, "EUR"));
    assertThat(resultOf("capture-1")).isEqualTo(AccountingRequestResults.SUCCESS);
  }

  @Test
  void cancellationResultRecordsCancelledOnSuccess() {
    seedPaymentTransaction("payment-cancel-ok");
    submit(
        AccountingRequestTypes.CANCELLATION_RESULT,
        "payment-cancel-ok",
        "payment-cancel-ok",
        true,
        null,
        null);

    processor().processNext();

    assertThat(ledger.events)
        .containsExactly(new RecordedEvent("payment-cancel-ok", null, "CANCELLED"));
    assertThat(resultOf("payment-cancel-ok")).isEqualTo(AccountingRequestResults.SUCCESS);
  }

  @Test
  void failedCancellationEndsFailedWithoutCallingLedger() {
    seedPaymentTransaction("payment-cancel-failed");
    submit(
        AccountingRequestTypes.CANCELLATION_RESULT,
        "payment-cancel-failed",
        "payment-cancel-failed",
        false,
        null,
        null);

    processor().processNext();

    assertThat(ledger.events).isEmpty();
    assertThat(resultOf("payment-cancel-failed")).isEqualTo(AccountingRequestResults.FAILED);
  }

  @Test
  void refundResultRecordsRefundedForTheNamedReference() {
    seedPaymentTransaction("payment-refund-result");
    submit(
        AccountingRequestTypes.REFUND_RESULT,
        "refund-result-1",
        "payment-refund-result",
        true,
        null,
        null);

    processor().processNext();

    assertThat(ledger.events)
        .containsExactly(new RecordedEvent("payment-refund-result", "refund-result-1", "REFUNDED"));
  }

  @Test
  void ledgerFailureEndsTheRequestFailedAndFreesThePaymentForTheNextRequest() {
    seedPaymentTransaction("payment-ledger-down");
    submit(
        AccountingRequestTypes.AUTHORISATION_RESULT,
        "payment-ledger-down",
        "payment-ledger-down",
        true,
        null,
        null);
    ledger.failNextEvent = true;

    processor().processNext();

    assertThat(resultOf("payment-ledger-down")).isEqualTo(AccountingRequestResults.FAILED);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_lock WHERE transaction_id = "
                    + "(SELECT transaction_id FROM transaction "
                    + "WHERE reference = 'payment-ledger-down')",
                Integer.class))
        .isZero();
  }

  @Test
  void failedRequestEmitsOneWarningWithOperationalReferences() {
    seedPaymentTransaction("payment-log-failure");
    submit(
        AccountingRequestTypes.AUTHORISATION_RESULT,
        "payment-log-failure",
        "payment-log-failure",
        true,
        null,
        null);
    ledger.failNextEvent = true;
    ListAppender<ILoggingEvent> appender = appender();

    try {
      processor().processNext();

      assertThat(appender.list).hasSize(1);
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
      assertThat(event.getMDCPropertyMap())
          .containsEntry("request_type", "AUTHORISATION_RESULT")
          .containsEntry("payment_reference", "payment-log-failure")
          .containsEntry("result", "FAILED")
          .containsEntry("merchant_account_id", "10")
          .containsKey("queue_id")
          .doesNotContainValue("demo-hmac-secret")
          .doesNotContainValue("demo-outpost-api-key")
          .doesNotContainValue("shopper@example.test")
          .doesNotContainValue("https://payments.example.test/link");
      assertThat(event.getThrowableProxy()).isNotNull();
    } finally {
      detach(appender);
    }
  }

  @Test
  void refundRequestDelegatesToTheMerchantRefundWorkflow() {
    seedPaymentTransaction("payment-refund-request");
    submit(
        AccountingRequestTypes.REFUND_REQUEST,
        "refund-request-1",
        "payment-refund-request",
        null,
        null,
        null);
    MerchantRefundWorkflow workflow = mock(MerchantRefundWorkflow.class);
    when(workflow.apply(any())).thenReturn(AccountingRequestResults.SUCCESS);

    new AccountingRequestProcessor(requests, ledger, workflow).processNext();

    verify(workflow).apply(any());
    assertThat(resultOf("refund-request-1")).isEqualTo(AccountingRequestResults.SUCCESS);
  }

  @Test
  void twoRequestsForOnePaymentRunSeriallyWhileDifferentPaymentsRunInParallel() throws Exception {
    seedPaymentTransaction("payment-a");
    seedPaymentTransaction("payment-b");
    submit(
        AccountingRequestTypes.AUTHORISATION_RESULT,
        "payment-a-first",
        "payment-a",
        true,
        null,
        null);
    submit(
        AccountingRequestTypes.CANCELLATION_RESULT,
        "payment-a-second",
        "payment-a",
        true,
        null,
        null);
    submit(
        AccountingRequestTypes.AUTHORISATION_RESULT,
        "payment-b-first",
        "payment-b",
        true,
        null,
        null);

    CountDownLatch inFlight = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    BlockingLedgerPaymentClient blocking = new BlockingLedgerPaymentClient(inFlight, release);
    AccountingRequestProcessor blockingProcessor =
        new AccountingRequestProcessor(requests, blocking, mock(MerchantRefundWorkflow.class));

    ExecutorService executor = Executors.newFixedThreadPool(3);
    try {
      List<Future<Boolean>> claims = new ArrayList<>();
      Callable<Boolean> claim = blockingProcessor::processNext;
      claims.add(executor.submit(claim));
      claims.add(executor.submit(claim));

      assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(blocking.inFlightPayments()).containsExactlyInAnyOrder("payment-a", "payment-b");
      release.countDown();
      for (Future<Boolean> claimed : claims) {
        assertThat(claimed.get(5, TimeUnit.SECONDS)).isTrue();
      }

      assertThat(blockingProcessor.processNext()).isTrue();
      assertThat(resultOf("payment-a-first")).isEqualTo(AccountingRequestResults.SUCCESS);
      assertThat(resultOf("payment-a-second")).isEqualTo(AccountingRequestResults.SUCCESS);
      assertThat(resultOf("payment-b-first")).isEqualTo(AccountingRequestResults.SUCCESS);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  private AccountingRequestProcessor processor() {
    return new AccountingRequestProcessor(requests, ledger, mock(MerchantRefundWorkflow.class));
  }

  private static ListAppender<ILoggingEvent> appender() {
    Logger logger = (Logger) LoggerFactory.getLogger(AccountingRequestProcessor.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static void detach(ListAppender<ILoggingEvent> appender) {
    Logger logger = (Logger) LoggerFactory.getLogger(AccountingRequestProcessor.class);
    logger.detachAppender(appender);
    appender.stop();
  }

  private void submit(
      AccountingRequestTypes type,
      String reference,
      String originalReference,
      @Nullable Boolean success,
      @Nullable Long amount,
      @Nullable Long currencyId) {
    requests.submit(
        new SubmitAccountingRequestCommand(
            type,
            reference,
            originalReference,
            10,
            null,
            "idempotency-" + reference,
            null,
            success,
            amount,
            currencyId,
            null,
            List.<AccountingRequestLine>of()));
  }

  private AccountingRequestResults resultOf(String reference) {
    long resultId =
        Objects.requireNonNull(
            jdbcTemplate.queryForObject(
                "SELECT result_id FROM accounting_request_queue WHERE reference = ?",
                Long.class,
                reference));
    return java.util.Arrays.stream(AccountingRequestResults.values())
        .filter(result -> result.getValue().accountingRequestResultId() == resultId)
        .findFirst()
        .orElseThrow();
  }

  private void seedPaymentTransaction(String paymentReference) {
    jdbcTemplate.update(
        "INSERT INTO transaction (transaction_type_id, account_id, reference, quantity, "
            + "currency_id, created_ts) VALUES (1, 10, ?, 1250, 3, ?)",
        paymentReference,
        Timestamp.from(Instant.parse("2026-09-12T10:00:00Z")));
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
    seed.update("INSERT INTO account_type VALUES (2, 'MERCHANT'), (4, 'PSP')");
    seed.update("INSERT INTO currency VALUES (3, 'EUR', 2)");
    seed.update(
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "VALUES (10, 2, 'merchant', 'Merchant', true, now()), "
            + "(11, 4, 'psp', 'PSP', true, now())");
    seed.update(
        "INSERT INTO transaction_type VALUES (1, 'PAYMENT'), (2, 'CAPTURE'), (3, 'REFUND')");
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

  private record RecordedCapture(
      String paymentReference,
      String captureReference,
      boolean success,
      long amount,
      String currency) {}

  private static final class FakeLedgerPaymentClient implements LedgerPaymentClient {
    private final List<RecordedEvent> events = new ArrayList<>();
    private final List<RecordedCapture> captures = new ArrayList<>();
    private boolean failNextEvent;

    void reset() {
      events.clear();
      captures.clear();
      failNextEvent = false;
    }

    @Override
    public void recordEvent(
        String paymentReference, @Nullable String refundReference, String event) {
      if (failNextEvent) {
        throw new LedgerPaymentClientException("stubbed failure");
      }
      events.add(new RecordedEvent(paymentReference, refundReference, event));
    }

    @Override
    public void recordCapture(
        String paymentReference,
        String captureReference,
        boolean success,
        long amount,
        String currency) {
      captures.add(
          new RecordedCapture(paymentReference, captureReference, success, amount, currency));
    }

    @Override
    public void reserveRefund(
        String paymentReference,
        String refundReference,
        long netAmount,
        long taxAmount,
        String currency) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class BlockingLedgerPaymentClient implements LedgerPaymentClient {
    private final CountDownLatch inFlight;
    private final CountDownLatch release;
    private final List<String> paymentsInFlight = new java.util.concurrent.CopyOnWriteArrayList<>();

    BlockingLedgerPaymentClient(CountDownLatch inFlight, CountDownLatch release) {
      this.inFlight = inFlight;
      this.release = release;
    }

    List<String> inFlightPayments() {
      return paymentsInFlight;
    }

    @Override
    public void recordEvent(
        String paymentReference, @Nullable String refundReference, String event) {
      paymentsInFlight.add(paymentReference);
      inFlight.countDown();
      await();
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
      throw new UnsupportedOperationException();
    }

    private void await() {
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          throw new AssertionError("Timed out waiting to release the Ledger call");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while holding the Ledger call", exception);
      }
    }
  }
}
