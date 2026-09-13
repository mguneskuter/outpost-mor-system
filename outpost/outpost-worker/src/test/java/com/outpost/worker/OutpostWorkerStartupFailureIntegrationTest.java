package com.outpost.worker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.account.AccountTypes;
import com.outpost.accounting.queue.AccountingRequestResults;
import com.outpost.accounting.queue.AccountingRequestStatuses;
import com.outpost.accounting.queue.AccountingRequestTypes;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.PspEventStatuses;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;

class OutpostWorkerStartupFailureIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_worker_startup", "outpost_worker_startup", "outpost_worker_startup");

  @BeforeAll
  static void migrateAndSeed() {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    seedReferenceData(jdbcTemplate());
  }

  private static void seedReferenceData(JdbcTemplate jdbcTemplate) {
    for (AccountTypes accountType : AccountTypes.values()) {
      var value = accountType.getValue();
      jdbcTemplate.update(
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          value.getAccountTypeId(),
          value.getCode());
    }
    for (Currencies currency : Currencies.values()) {
      var value = currency.getValue();
      jdbcTemplate.update(
          "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
          value.getCurrencyId(),
          value.getCurrencyCode(),
          value.getExponent());
    }
    for (AccountingRequestTypes type : AccountingRequestTypes.values()) {
      jdbcTemplate.update(
          "INSERT INTO accounting_request_type "
              + "(accounting_request_type_id, accounting_request_type_code) VALUES (?, ?)",
          type.getValue().accountingRequestTypeId(),
          type.getValue().code());
    }
    for (AccountingRequestStatuses status : AccountingRequestStatuses.values()) {
      jdbcTemplate.update(
          "INSERT INTO accounting_request_status "
              + "(accounting_request_status_id, accounting_request_status_code) VALUES (?, ?)",
          status.getValue().accountingRequestStatusId(),
          status.getValue().code());
    }
    for (AccountingRequestResults result : AccountingRequestResults.values()) {
      jdbcTemplate.update(
          "INSERT INTO accounting_request_result "
              + "(accounting_request_result_id, accounting_request_result_code) VALUES (?, ?)",
          result.getValue().accountingRequestResultId(),
          result.getValue().code());
    }
    for (PspEventCodes code : PspEventCodes.values()) {
      jdbcTemplate.update(
          "INSERT INTO psp_event_code (psp_event_code_id, code) VALUES (?, ?)",
          code.getValue().getPspEventCodeId(),
          code.getValue().getCode());
    }
    for (PspEventStatuses status : PspEventStatuses.values()) {
      jdbcTemplate.update(
          "INSERT INTO psp_event_status (psp_event_status_id, code) VALUES (?, ?)",
          status.getValue().getPspEventStatusId(),
          status.getValue().getCode());
    }
    for (PspEventResults result : PspEventResults.values()) {
      jdbcTemplate.update(
          "INSERT INTO psp_event_result (psp_event_result_id, code) VALUES (?, ?)",
          result.getValue().getPspEventResultId(),
          result.getValue().getCode());
    }
  }

  private static JdbcTemplate jdbcTemplate() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(DATABASE.getJdbcUrl());
    dataSource.setUsername(DATABASE.getUsername());
    dataSource.setPassword(DATABASE.getPassword());
    return new JdbcTemplate(dataSource);
  }

  @Test
  void failsClosedWhenLedgerSigningSecretIsAbsent() {
    assertThatThrownBy(() -> start(/* ledgerHmacSecret= */ null))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining("hmacSecret");
  }

  @Test
  void failsClosedWhenLedgerSigningSecretIsBlank() {
    assertThatThrownBy(() -> start(""))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining("hmacSecret");
  }

  @ParameterizedTest
  @MethodSource("invalidOperationalSettings")
  void failsClosedForInvalidOperationalSetting(String property, String value, String fieldName) {
    assertThatThrownBy(() -> start("worker-test-key", property, value))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining(fieldName);
  }

  private static Stream<Arguments> invalidOperationalSettings() {
    return Stream.of(
        Arguments.of("outpost.worker.psp.worker-count", "0", "workerCount"),
        Arguments.of("outpost.worker.psp.worker-count", "-1", "workerCount"),
        Arguments.of("outpost.worker.psp.worker-count", "65", "workerCount"),
        Arguments.of("outpost.worker.accounting.poll-interval", "PT0S", "pollInterval"),
        Arguments.of("outpost.worker.accounting.poll-interval", "-PT1S", "pollInterval"),
        Arguments.of("outpost.worker.accounting.poll-interval", "PT2M", "pollInterval"),
        Arguments.of("outpost.worker.ledger.connect-timeout", "PT0S", "connectTimeout"),
        Arguments.of("outpost.worker.ledger.connect-timeout", "-PT1S", "connectTimeout"),
        Arguments.of("outpost.worker.ledger.read-timeout", "PT6M", "readTimeout"),
        Arguments.of("outpost.worker.ledger.base-url", "not-a-url", "baseUrl"));
  }

  private void start(@Nullable String ledgerHmacSecret) {
    start(ledgerHmacSecret, null, null);
  }

  private void start(
      @Nullable String ledgerHmacSecret, @Nullable String property, @Nullable String value) {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("spring.datasource.url", DATABASE.getJdbcUrl());
    env.setProperty("spring.datasource.username", DATABASE.getUsername());
    env.setProperty("spring.datasource.password", DATABASE.getPassword());
    env.setProperty("outpost.worker.psp.enabled", "false");
    env.setProperty("outpost.worker.accounting.enabled", "false");
    env.setProperty("outpost.worker.ledger.base-url", "http://localhost:8081");
    if (ledgerHmacSecret != null) {
      env.setProperty("outpost.worker.ledger.hmac-secret", ledgerHmacSecret);
    }
    if (property != null && value != null) {
      env.setProperty(property, value);
    }
    new SpringApplicationBuilder(OutpostWorkerApplication.class)
        .web(WebApplicationType.NONE)
        .environment(env)
        .run();
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
