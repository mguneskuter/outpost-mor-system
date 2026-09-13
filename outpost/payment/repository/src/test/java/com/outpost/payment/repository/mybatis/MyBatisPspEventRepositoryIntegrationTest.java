package com.outpost.payment.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.AccountTypes;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.repository.PspEventRepository.PspEvent;
import com.outpost.payment.repository.PspEventRepository.ReceivedPspEvent;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class MyBatisPspEventRepositoryIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_psp_event_repository",
          "outpost_psp_event_repository",
          "outpost_psp_event_repository");
  private static @Nullable HikariDataSource dataSource;
  private static @Nullable MyBatisPspEventRepository events;
  private static @Nullable JdbcTemplate jdbcTemplate;
  private static long merchantAccountId;
  private static long pspAccountId;

  @BeforeAll
  static void migrateAndSeed() throws Exception {
    DATABASE.start();
    HikariDataSource database =
        DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .url(DATABASE.getJdbcUrl())
            .username(DATABASE.getUsername())
            .password(DATABASE.getPassword())
            .build();
    dataSource = database;
    Flyway.configure()
        .dataSource(database)
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
    sessionFactory.setDataSource(database);
    sessionFactory.setMapperLocations(new ClassPathResource("db/mapper/PspEventQueueMapper.xml"));
    SqlSessionTemplate sessions =
        new SqlSessionTemplate(Objects.requireNonNull(sessionFactory.getObject()));
    events = new MyBatisPspEventRepository(sessions.getMapper(PspEventQueueMapper.class));
    JdbcTemplate jdbc = new JdbcTemplate(database);
    jdbcTemplate = jdbc;
    seedReferenceDataWithRotatedStatusIdentifiers(jdbc);
    merchantAccountId = account(jdbc, AccountTypes.MERCHANT, "PSP_EVENT_MERCHANT");
    pspAccountId = account(jdbc, AccountTypes.PSP, "PSP_EVENT_PSP");
  }

  @AfterAll
  static void closeDatabase() {
    if (dataSource != null) {
      dataSource.close();
    }
    DATABASE.stop();
  }

  @Test
  void eventStatusesResolveByCodeRegardlessOfSeededIdentifiers() {
    events()
        .recordReceived(
            new ReceivedPspEvent(
                merchantAccountId,
                pspAccountId,
                "event-1",
                "payment-1",
                PspEventCodes.AUTHORISATION,
                "{}"));

    assertThat(statusCodeOf("event-1")).isEqualTo("RECEIVED");

    PspEvent claimed = events().claimNext().orElseThrow();

    assertThat(statusCodeOf("event-1")).isEqualTo("IN_PROGRESS");

    events().complete(claimed.queueId(), PspEventResults.SUCCESS);

    assertThat(statusCodeOf("event-1")).isEqualTo("DONE");
  }

  @Test
  void completingAnEventStoresItsDoneTimeAsTheWritingTransactionTime() {
    events()
        .recordReceived(
            new ReceivedPspEvent(
                merchantAccountId,
                pspAccountId,
                "event-2",
                "payment-2",
                PspEventCodes.AUTHORISATION,
                "{}"));
    PspEvent claimed = events().claimNext().orElseThrow();

    transactions()
        .executeWithoutResult(
            status -> {
              events().complete(claimed.queueId(), PspEventResults.SUCCESS);

              assertThat(
                      jdbc()
                          .queryForObject(
                              "SELECT done_ts FROM psp_event_queue WHERE queue_id = ?",
                              Instant.class,
                              claimed.queueId()))
                  .isEqualTo(jdbc().queryForObject("SELECT now()", Instant.class));
            });
  }

  private static TransactionTemplate transactions() {
    return new TransactionTemplate(
        new DataSourceTransactionManager(Objects.requireNonNull(dataSource)));
  }

  private static JdbcTemplate jdbc() {
    return Objects.requireNonNull(jdbcTemplate);
  }

  private static MyBatisPspEventRepository events() {
    return Objects.requireNonNull(events);
  }

  private static String statusCodeOf(String reference) {
    return Objects.requireNonNull(
        Objects.requireNonNull(jdbcTemplate)
            .queryForObject(
                "SELECT status.code FROM psp_event_queue queue "
                    + "JOIN psp_event_status status "
                    + "ON status.psp_event_status_id = queue.status_id "
                    + "WHERE queue.reference = ?",
                String.class,
                reference));
  }

  private static void seedReferenceDataWithRotatedStatusIdentifiers(JdbcTemplate jdbc) {
    for (AccountTypes type : List.of(AccountTypes.MERCHANT, AccountTypes.PSP)) {
      jdbc.update(
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          type.getValue().getAccountTypeId(),
          type.getValue().getCode());
    }
    for (PspEventCodes code : PspEventCodes.values()) {
      jdbc.update(
          "INSERT INTO psp_event_code (psp_event_code_id, code) VALUES (?, ?)",
          code.getValue().getPspEventCodeId(),
          code.getValue().getCode());
    }
    for (PspEventResults result : PspEventResults.values()) {
      jdbc.update(
          "INSERT INTO psp_event_result (psp_event_result_id, code) VALUES (?, ?)",
          result.getValue().getPspEventResultId(),
          result.getValue().getCode());
    }
    jdbc.update(
        "INSERT INTO psp_event_status (psp_event_status_id, code) "
            + "VALUES (3, 'RECEIVED'), (1, 'IN_PROGRESS'), (2, 'DONE')");
  }

  private static long account(JdbcTemplate jdbc, AccountTypes type, String code) {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "INSERT INTO account (account_type_id, code, name, is_active, created_ts) "
                + "VALUES (?, ?, ?, true, now()) RETURNING account_id",
            Long.class,
            type.getValue().getAccountTypeId(),
            code,
            code));
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
