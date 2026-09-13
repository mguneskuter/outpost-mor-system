package com.outpost.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.accounting.queue.AccountingRequestResults;
import com.outpost.accounting.queue.AccountingRequestStatuses;
import com.outpost.accounting.queue.AccountingRequestTypes;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.PspEventStatuses;
import com.outpost.payment.repository.PspEventRepository;
import com.outpost.payment.repository.PspEventRepository.PspEvent;
import com.outpost.worker.psp.PspEventProcessor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    classes = OutpostWorkerApplication.class,
    properties = {
      "outpost.worker.psp.enabled=false",
      "outpost.worker.ledger.base-url=http://localhost:8081",
      "outpost.worker.ledger.hmac-secret=test-worker-key"
    })
class WorkerIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer("outpost_worker", "outpost_worker", "outpost_worker");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PspEventProcessor processor;
  @Autowired private PspEventRepository events;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeAll
  static void migrateAndSeed() {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    JdbcTemplate seed = new JdbcTemplate(dataSource());
    seedReferenceData(seed);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Test
  void createsAndCompletesCaptureRequestInOneTransaction() {
    jdbcTemplate.update(
        "INSERT INTO psp_event_queue "
            + "(queue_id, created_ts, status_id, type_id, reference, original_reference, "
            + "account_id, account_type_id, psp_account_id, psp_account_type_id, payload) "
            + "VALUES (101, ?, 1, 2, 'capture-event', 'payment-reference', 10, 2, 11, 4, "
            + "CAST(? AS jsonb))",
        Timestamp.from(Instant.parse("2026-09-12T10:00:00Z")),
        """
        {"psp_reference":"capture-psp-reference","success":true,"amount":1250,"currency":"EUR"}
        """);

    assertThat(processor.processNext()).isTrue();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounting_request_queue WHERE psp_event_queue_id = 101",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT done FROM psp_event_queue WHERE queue_id = 101", Boolean.class))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT result_id FROM psp_event_queue WHERE queue_id = 101", Long.class))
        .isEqualTo(PspEventResults.SUCCESS.getValue().getPspEventResultId());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT type_id FROM accounting_request_queue WHERE psp_event_queue_id = 101",
                Long.class))
        .isEqualTo(AccountingRequestTypes.CAPTURE_RESULT.getValue().accountingRequestTypeId());
  }

  @Test
  void claimsOldestEventPerPaymentWhileDifferentPaymentsProceedInParallel() throws Exception {
    insertEvent(201, "first-payment-event", "payment-a", "2026-09-12T10:01:00Z");
    insertEvent(202, "later-payment-event", "payment-a", "2026-09-12T10:02:00Z");
    insertEvent(203, "other-payment-event", "payment-b", "2026-09-12T10:03:00Z");
    CountDownLatch claimed = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Callable<PspEvent> claim =
          () ->
              new TransactionTemplate(transactionManager)
                  .execute(
                      ignored -> {
                        PspEvent event = events.claimNext().orElseThrow();
                        claimed.countDown();
                        try {
                          if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to release claims");
                          }
                        } catch (InterruptedException exception) {
                          Thread.currentThread().interrupt();
                          throw new AssertionError("Interrupted while holding claim", exception);
                        }
                        return event;
                      });
      Future<PspEvent> first = executor.submit(claim);
      Future<PspEvent> second = executor.submit(claim);

      assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue();
      release.countDown();

      assertThat(List.of(first.get(), second.get()))
          .extracting(PspEvent::reference)
          .containsExactlyInAnyOrder("first-payment-event", "other-payment-event");
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  private void insertEvent(
      long queueId, String reference, String paymentReference, String createdAt) {
    jdbcTemplate.update(
        "INSERT INTO psp_event_queue "
            + "(queue_id, created_ts, status_id, type_id, reference, original_reference, "
            + "account_id, account_type_id, psp_account_id, psp_account_type_id, payload) "
            + "VALUES (?, ?, 1, 1, ?, ?, 10, 2, 11, 4, CAST(? AS jsonb))",
        queueId,
        Timestamp.from(Instant.parse(createdAt)),
        reference,
        paymentReference,
        """
        {"psp_reference":"psp-reference","success":true,"amount":1250,"currency":"EUR"}
        """);
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
}
