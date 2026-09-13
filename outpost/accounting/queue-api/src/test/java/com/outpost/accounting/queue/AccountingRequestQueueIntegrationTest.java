package com.outpost.accounting.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.accounting.queue.configuration.AccountingRequestQueueConfiguration;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = AccountingRequestQueueIntegrationTest.TestApplication.class)
class AccountingRequestQueueIntegrationTest {

  private static final Instant START = Instant.parse("2026-09-12T10:00:00Z");
  private static final MutableClock CLOCK = new MutableClock(START);

  @org.springframework.beans.factory.annotation.Autowired private JdbcTemplate jdbcTemplate;
  @org.springframework.beans.factory.annotation.Autowired private AccountingRequestQueue queue;

  @org.springframework.beans.factory.annotation.Autowired
  private PlatformTransactionManager transactionManager;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresTestDatabase.registerDataSourceProperties(registry);
  }

  @BeforeEach
  void migrateAndSeed() {
    DataSource dataSource = Objects.requireNonNull(jdbcTemplate.getDataSource());
    Flyway.configure()
        .dataSource(dataSource)
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .cleanDisabled(false)
        .load()
        .clean();
    Flyway.configure()
        .dataSource(dataSource)
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .load()
        .migrate();
    CLOCK.setInstant(START);
    seedReferenceData();
  }

  @Test
  void submitStoresReceivedRequestAndLines() {
    AccountingRequest request =
        queue.submit(
            command(
                "request-1",
                "payment-1",
                "idempotency-1",
                List.of(
                    new AccountingRequestLine("line-1", 125L),
                    new AccountingRequestLine("line-2"))));

    assertThat(request.getStatus()).isEqualTo(AccountingRequestStatuses.RECEIVED);
    assertThat(request.isDone()).isFalse();
    assertThat(request.getResult()).isNull();
    assertThat(request.getLines())
        .extracting(AccountingRequestLine::orderLineReference)
        .containsExactly("line-1", "line-2");
    assertThat(request.getLines().get(0).amount()).isEqualTo(125L);
  }

  @Test
  void submitJoinsCallerTransactionAndRollsBackWithIt() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              queue.submit(command("rolled-back", "payment-1", "rollback-key", List.of()));
              status.setRollbackOnly();
            });

    assertThat(countRequests()).isZero();
  }

  @Test
  void submitReturnsStoredRequestForDuplicateIdempotencyKey() {
    AccountingRequest first =
        queue.submit(
            command(
                "request-1",
                "payment-1",
                "same-key",
                List.of(new AccountingRequestLine("line-1"))));
    CLOCK.advance(Duration.ofSeconds(1));
    AccountingRequest duplicate =
        queue.submit(
            command(
                "request-2",
                "payment-2",
                "same-key",
                List.of(new AccountingRequestLine("line-2"))));

    assertThat(duplicate.getQueueId()).isEqualTo(first.getQueueId());
    assertThat(duplicate.getLines())
        .extracting(AccountingRequestLine::orderLineReference)
        .containsExactly("line-1");
    assertThat(countRequests()).isOne();
  }

  @Test
  void submitReturnsStoredRequestForDuplicatePspEventKey() {
    AccountingRequest first = queue.submit(commandWithPspEvent("request-1", "payment-1", 500L));

    AccountingRequest duplicate = queue.submit(commandWithPspEvent("request-2", "payment-2", 500L));

    assertThat(duplicate.getQueueId()).isEqualTo(first.getQueueId());
    assertThat(countRequests()).isOne();
  }

  @Test
  void schemaEnforcesQueueKeysCompletionAndOneLockPerPayment() {
    insertRawRequest(9000L, 1L, "schema-reference", "payment-1", null, null);
    assertThatThrownBy(
            () -> insertRawRequest(9001L, 1L, "schema-reference", "payment-2", null, null))
        .isInstanceOf(DataAccessException.class);

    insertRawRequest(9002L, 2L, "schema-reference-2", "payment-1", null, "schema-key");
    assertThatThrownBy(
            () ->
                insertRawRequest(9003L, 2L, "schema-reference-3", "payment-1", null, "schema-key"))
        .isInstanceOf(DataAccessException.class);

    insertRawRequest(9004L, 3L, "schema-reference-4", "payment-1", 500L, null);
    assertThatThrownBy(
            () -> insertRawRequest(9005L, 4L, "schema-reference-5", "payment-1", 500L, null))
        .isInstanceOf(DataAccessException.class);

    insertPayment(2000L, "payment-1");
    insertRawRequest(9006L, 1L, "schema-reference-6", "payment-1", null, null);
    jdbcTemplate.update(
        "INSERT INTO payment_lock (transaction_id, queue_id, locked_ts, lease_until_ts) "
            + "VALUES (?, ?, ?, ?)",
        2000L,
        9006L,
        timestamp(START),
        timestamp(START.plus(Duration.ofMinutes(5))));
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO payment_lock (transaction_id, queue_id, locked_ts, "
                        + "lease_until_ts) "
                        + "VALUES (?, ?, ?, ?)",
                    2000L,
                    9004L,
                    timestamp(START),
                    timestamp(START.plus(Duration.ofMinutes(5)))))
        .isInstanceOf(DataAccessException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO accounting_request_queue "
                        + "(queue_id, status_id, type_id, reference, original_reference, "
                        + "account_id, account_type_id, created_ts) "
                        + "VALUES (9007, 1, 1, 'schema-reference-7', 'payment-1', 101, 4, ?)",
                    timestamp(START)))
        .isInstanceOf(DataAccessException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE accounting_request_queue SET status_id = 3 WHERE queue_id = 9000"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE accounting_request_queue SET done = true WHERE queue_id = 9000"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void claimNextReturnsOldestRequestAndCompletionReleasesPayment() {
    insertPayment(2001L, "payment-1");
    AccountingRequest oldest = queue.submit(command("request-1", "payment-1", "key-1", List.of()));
    CLOCK.advance(Duration.ofSeconds(1));
    final AccountingRequest next =
        queue.submit(command("request-2", "payment-1", "key-2", List.of()));

    Optional<AccountingRequest> claimed = queue.claimNext();

    assertThat(claimed).isPresent();
    assertThat(claimed.orElseThrow().getQueueId()).isEqualTo(oldest.getQueueId());
    assertThat(claimed.orElseThrow().getStatus()).isEqualTo(AccountingRequestStatuses.IN_PROGRESS);
    assertThat(queue.claimNext()).isEmpty();

    queue.complete(claimed.orElseThrow(), AccountingRequestResults.SUCCESS);

    Optional<AccountingRequest> secondClaim = queue.claimNext();
    assertThat(secondClaim).isPresent();
    assertThat(secondClaim.orElseThrow().getQueueId()).isEqualTo(next.getQueueId());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_lock WHERE transaction_id = ?", Integer.class, 2001L))
        .isEqualTo(1);
  }

  @Test
  void claimNextSkipsLiveLockAndTakesOverExpiredLock() {
    insertPayment(2002L, "payment-1");
    AccountingRequest request = queue.submit(command("request-1", "payment-1", "key-1", List.of()));
    jdbcTemplate.update(
        "INSERT INTO payment_lock (transaction_id, queue_id, locked_ts, lease_until_ts) "
            + "VALUES (?, ?, ?, ?)",
        2002L,
        request.getQueueId(),
        timestamp(START),
        timestamp(START.plus(Duration.ofHours(1))));

    assertThat(queue.claimNext()).isEmpty();

    CLOCK.advance(Duration.ofHours(1));

    assertThat(queue.claimNext())
        .get()
        .extracting(AccountingRequest::getQueueId)
        .isEqualTo(request.getQueueId());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT lease_until_ts FROM payment_lock WHERE transaction_id = ?",
                Instant.class,
                2002L))
        .isEqualTo(START.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(5)));
  }

  @Test
  void missingPaymentIsCompletedAsFailed() {
    AccountingRequest submitted =
        queue.submit(command("request-1", "unknown-payment", "key-1", List.of()));

    assertThat(queue.claimNext()).isEmpty();
    AccountingRequest completed =
        queue.submit(command("request-1", "unknown-payment", "key-1", List.of()));

    assertThat(completed.getQueueId()).isEqualTo(submitted.getQueueId());
    assertThat(completed.isDone()).isTrue();
    assertThat(completed.getStatus()).isEqualTo(AccountingRequestStatuses.DONE);
    assertThat(completed.getResult()).isEqualTo(AccountingRequestResults.FAILED);
  }

  @Test
  void claimAndCompleteResolveStatusByCodeRegardlessOfSeededIdentifiers() {
    rotateStatusIdentifiers();
    insertPayment(2010L, "payment-1");
    AccountingRequest submitted =
        queue.submit(command("request-1", "payment-1", "key-1", List.of()));

    queue.claimNext();

    assertThat(statusCodeOf(submitted.getQueueId())).isEqualTo("IN_PROGRESS");

    queue.complete(submitted.getQueueId(), AccountingRequestResults.SUCCESS);

    assertThat(statusCodeOf(submitted.getQueueId())).isEqualTo("DONE");
  }

  @Test
  void missingPaymentIsCompletedWithStatusAndResultResolvedByCodeRegardlessOfSeededIdentifiers() {
    rotateStatusIdentifiers();
    rotateResultIdentifiers();
    AccountingRequest submitted =
        queue.submit(command("request-1", "unknown-payment", "key-1", List.of()));

    assertThat(queue.claimNext()).isEmpty();

    assertThat(statusCodeOf(submitted.getQueueId())).isEqualTo("DONE");
    assertThat(resultCodeOf(submitted.getQueueId())).isEqualTo("FAILED");
  }

  @Test
  void concurrentClaimsGiveOnePaymentToOnlyOneCaller() throws Exception {
    insertPayment(2003L, "payment-1");
    queue.submit(command("request-1", "payment-1", "key-1", List.of()));

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Optional<AccountingRequest>> claim =
          () -> {
            ready.countDown();
            start.await();
            return queue.claimNext();
          };
      Future<Optional<AccountingRequest>> first = executor.submit(claim);
      Future<Optional<AccountingRequest>> second = executor.submit(claim);
      ready.await();
      start.countDown();

      List<Optional<AccountingRequest>> results = List.of(first.get(), second.get());
      assertThat(results.stream().filter(Optional::isPresent)).hasSize(1);
      assertThat(results.stream().filter(Optional::isEmpty)).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableOutpostPersistence
  @Import(AccountingRequestQueueConfiguration.class)
  static class TestApplication {
    @Bean
    @Primary
    Clock accountingRequestQueueTestClock() {
      return CLOCK;
    }
  }

  private void seedReferenceData() {
    jdbcTemplate.update(
        "INSERT INTO account_type (account_type_id, code) VALUES (2, 'MERCHANT'), (4, 'PSP')");
    jdbcTemplate.update(
        "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (1, 'EUR', 2)");
    jdbcTemplate.update(
        "INSERT INTO transaction_type (transaction_type_id, code) VALUES (1, 'PAYMENT')");
    jdbcTemplate.update(
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "VALUES (100, 2, 'merchant', 'Merchant', true, ?), (101, 4, 'psp', 'PSP', true, ?)",
        timestamp(START),
        timestamp(START));
    for (AccountingRequestTypes type : AccountingRequestTypes.values()) {
      jdbcTemplate.update(
          "INSERT INTO accounting_request_type "
              + "(accounting_request_type_id, accounting_request_type_code) "
              + "VALUES (?, ?)",
          type.getValue().accountingRequestTypeId(),
          type.getValue().code());
    }
    for (AccountingRequestStatuses status : AccountingRequestStatuses.values()) {
      jdbcTemplate.update(
          "INSERT INTO accounting_request_status_type "
              + "(accounting_request_status_type_id, accounting_request_status_type_code) "
              + "VALUES (?, ?)",
          status.getValue().accountingRequestStatusId(),
          status.getValue().code());
    }
    for (AccountingRequestResults result : AccountingRequestResults.values()) {
      jdbcTemplate.update(
          "INSERT INTO accounting_request_result_type "
              + "(accounting_request_result_type_id, accounting_request_result_type_code) "
              + "VALUES (?, ?)",
          result.getValue().accountingRequestResultId(),
          result.getValue().code());
    }
    jdbcTemplate.update("INSERT INTO psp_event_code VALUES (1, 'AUTHORISATION')");
    jdbcTemplate.update("INSERT INTO psp_event_status VALUES (1, 'RECEIVED'), (3, 'DONE')");
    jdbcTemplate.update("INSERT INTO psp_event_result VALUES (1, 'SUCCESS')");
    jdbcTemplate.update(
        "INSERT INTO psp_event_queue "
            + "(queue_id, created_ts, status_id, type_id, reference, original_reference, "
            + "account_id, account_type_id, psp_account_id, psp_account_type_id, payload) "
            + "VALUES (500, ?, 1, 1, 'event-1', 'payment-1', 100, 2, 101, 4, '{}'::jsonb)",
        timestamp(START));
  }

  private SubmitAccountingRequestCommand command(
      String reference,
      String originalReference,
      String idempotencyKey,
      List<AccountingRequestLine> lines) {
    return new SubmitAccountingRequestCommand(
        AccountingRequestTypes.REFUND_REQUEST,
        reference,
        originalReference,
        100L,
        idempotencyKey,
        lines);
  }

  private SubmitAccountingRequestCommand commandWithPspEvent(
      String reference, String originalReference, long pspEventQueueId) {
    return new SubmitAccountingRequestCommand(
        AccountingRequestTypes.CAPTURE_RESULT,
        reference,
        originalReference,
        100L,
        pspEventQueueId,
        null,
        null,
        true,
        100L,
        1L,
        "psp-reference",
        List.of());
  }

  private void insertPayment(long transactionId, String reference) {
    jdbcTemplate.update(
        "INSERT INTO transaction "
            + "(transaction_id, transaction_type_id, account_id, reference, quantity, "
            + "currency_id, created_ts) "
            + "VALUES (?, 1, 100, ?, 100, 1, ?)",
        transactionId,
        reference,
        timestamp(START));
  }

  private void insertRawRequest(
      long queueId,
      long typeId,
      String reference,
      String originalReference,
      @Nullable Long pspEventQueueId,
      @Nullable String idempotencyKey) {
    jdbcTemplate.update(
        "INSERT INTO accounting_request_queue "
            + "(queue_id, status_id, type_id, reference, original_reference, account_id, "
            + "account_type_id, "
            + "psp_event_queue_id, idempotency_key, created_ts) "
            + "VALUES (?, 1, ?, ?, ?, 100, 2, ?, ?, ?)",
        queueId,
        typeId,
        reference,
        originalReference,
        pspEventQueueId,
        idempotencyKey,
        timestamp(START));
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.from(instant);
  }

  private int countRequests() {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM accounting_request_queue", Integer.class));
  }

  private void rotateStatusIdentifiers() {
    jdbcTemplate.update("DELETE FROM accounting_request_status_type");
    jdbcTemplate.update(
        "INSERT INTO accounting_request_status_type "
            + "(accounting_request_status_type_id, accounting_request_status_type_code) "
            + "VALUES (2, 'RECEIVED'), (3, 'IN_PROGRESS'), (1, 'DONE')");
  }

  private void rotateResultIdentifiers() {
    jdbcTemplate.update("DELETE FROM accounting_request_result_type");
    jdbcTemplate.update(
        "INSERT INTO accounting_request_result_type "
            + "(accounting_request_result_type_id, accounting_request_result_type_code) "
            + "VALUES (2, 'SUCCESS'), (1, 'FAILED')");
  }

  private String statusCodeOf(long queueId) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT s.accounting_request_status_type_code FROM accounting_request_queue q "
                + "JOIN accounting_request_status_type s "
                + "ON s.accounting_request_status_type_id = q.status_id "
                + "WHERE q.queue_id = ?",
            String.class,
            queueId));
  }

  private String resultCodeOf(long queueId) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT r.accounting_request_result_type_code FROM accounting_request_queue q "
                + "JOIN accounting_request_result_type r "
                + "ON r.accounting_request_result_type_id = q.result_id "
                + "WHERE q.queue_id = ?",
            String.class,
            queueId));
  }

  private static final class MutableClock extends Clock {
    private volatile Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void setInstant(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
