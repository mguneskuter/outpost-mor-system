package com.outpost.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;

class LedgerApiStartupFailureIntegrationTest {
  private static @Nullable DataSource seedDataSource;

  private static final String GATEWAY_SECRET_ENVIRONMENT_VARIABLE =
      "OUTPOST_LEDGER_GATEWAY_HMAC_SECRET";
  private static final String WORKER_SECRET_ENVIRONMENT_VARIABLE =
      "OUTPOST_LEDGER_WORKER_HMAC_SECRET";

  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_ledger_startup", "outpost_ledger_startup", "outpost_ledger_startup");

  static {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
  }

  @BeforeEach
  void clearPersistedData() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    jdbcTemplate.update("DELETE FROM fx_rate");
    jdbcTemplate.update("DELETE FROM fx_fee");
    jdbcTemplate.update("DELETE FROM account_type_register_type");
    jdbcTemplate.update("DELETE FROM register_type");
    jdbcTemplate.update("DELETE FROM account_type");
    jdbcTemplate.update("DELETE FROM transaction_event_type");
    jdbcTemplate.update("DELETE FROM transaction_type");
    jdbcTemplate.update("DELETE FROM journal_entry_type");
    jdbcTemplate.update("DELETE FROM currency");
  }

  @Test
  void failsWhenCurrencyIdentityIsUnknown() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    LedgerStaticDataFixtures.materialize(jdbcTemplate);
    LedgerStaticDataFixtures.insertRates(jdbcTemplate, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(jdbcTemplate, true);
    jdbcTemplate.update("UPDATE currency SET currency_code = 'ZZZ' WHERE currency_id = 3");

    assertThatThrownBy(this::startAgainstDatabase)
        .isInstanceOf(Exception.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void verifiesStaticDataBeforeLoadingFxData() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    LedgerStaticDataFixtures.materialize(jdbcTemplate);
    LedgerStaticDataFixtures.insertRates(jdbcTemplate, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(jdbcTemplate, true);
    jdbcTemplate.update(
        "UPDATE register_type SET register_type_code = 'INVALID' WHERE register_type_id = 1");

    assertThatThrownBy(this::startAgainstDatabase)
        .isInstanceOf(Exception.class)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("System sanity check failed");
  }

  @Test
  void startsWhenStoredRateDateLacksPair() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    LedgerStaticDataFixtures.materialize(jdbcTemplate);
    LedgerStaticDataFixtures.insertRates(jdbcTemplate, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertRates(jdbcTemplate, LocalDate.of(2026, 9, 11), false);
    LedgerStaticDataFixtures.insertFees(jdbcTemplate, true);

    try (ConfigurableApplicationContext context =
        startAgainstDatabase("gateway-secret", "worker-secret")) {
      assertThat(context.isActive()).isTrue();
    }
  }

  @Test
  void failsWhenFeeLacksPair() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    LedgerStaticDataFixtures.materialize(jdbcTemplate);
    LedgerStaticDataFixtures.insertRates(jdbcTemplate, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(jdbcTemplate, false);

    assertThatThrownBy(this::startAgainstDatabase)
        .isInstanceOf(Exception.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsClosedWhenGatewaySigningSecretIsBlank() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    LedgerStaticDataFixtures.materialize(jdbcTemplate);
    LedgerStaticDataFixtures.insertRates(jdbcTemplate, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(jdbcTemplate, true);

    assertThatThrownBy(() -> startAgainstDatabase("", /* workerHmacSecret= */ null))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining("gatewayHmacSecret");
  }

  @Test
  void failsClosedWhenGatewaySigningSecretIsAbsent() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    LedgerStaticDataFixtures.materialize(jdbcTemplate);
    LedgerStaticDataFixtures.insertRates(jdbcTemplate, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(jdbcTemplate, true);

    assertThatThrownBy(
            () ->
                startAgainstDatabase(
                    unresolvedPlaceholder(GATEWAY_SECRET_ENVIRONMENT_VARIABLE),
                    /* workerHmacSecret= */ null))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining("gatewayHmacSecret");
  }

  @Test
  void failsClosedWhenWorkerSigningSecretIsBlank() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    LedgerStaticDataFixtures.materialize(jdbcTemplate);
    LedgerStaticDataFixtures.insertRates(jdbcTemplate, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(jdbcTemplate, true);

    assertThatThrownBy(() -> startAgainstDatabase(/* gatewayHmacSecret= */ null, ""))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining("workerHmacSecret");
  }

  @Test
  void failsClosedWhenWorkerSigningSecretIsAbsent() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    LedgerStaticDataFixtures.materialize(jdbcTemplate);
    LedgerStaticDataFixtures.insertRates(jdbcTemplate, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(jdbcTemplate, true);

    assertThatThrownBy(
            () ->
                startAgainstDatabase(
                    /* gatewayHmacSecret= */ null,
                    unresolvedPlaceholder(WORKER_SECRET_ENVIRONMENT_VARIABLE)))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining("workerHmacSecret");
  }

  @Test
  void failsWhenDatabaseIsUnreachable() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:1/unreachable");
    environment.setProperty("spring.datasource.username", "outpost");
    environment.setProperty("spring.datasource.password", "outpost");

    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(LedgerApiApplication.class)
                    .web(WebApplicationType.NONE)
                    .environment(environment)
                    .run())
        .isInstanceOf(Exception.class);
  }

  private void startAgainstDatabase() {
    startAgainstDatabase(/* gatewayHmacSecret= */ null, /* workerHmacSecret= */ null);
  }

  private ConfigurableApplicationContext startAgainstDatabase(
      @Nullable String gatewayHmacSecret, @Nullable String workerHmacSecret) {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("spring.datasource.url", DATABASE.getJdbcUrl());
    environment.setProperty("spring.datasource.username", DATABASE.getUsername());
    environment.setProperty("spring.datasource.password", DATABASE.getPassword());
    if (gatewayHmacSecret != null) {
      environment.setProperty("outpost.ledger.gateway-hmac-secret", gatewayHmacSecret);
    }
    if (workerHmacSecret != null) {
      environment.setProperty("outpost.ledger.worker-hmac-secret", workerHmacSecret);
    }
    return new SpringApplicationBuilder(LedgerApiApplication.class)
        .web(WebApplicationType.NONE)
        .environment(environment)
        .run();
  }

  /**
   * Reproduces the property's own unresolved default from {@code application.properties}, so a test
   * using it exercises a genuinely absent environment variable rather than an explicit blank
   * override.
   */
  private static String unresolvedPlaceholder(String environmentVariable) {
    return "${" + environmentVariable + ":}";
  }

  private static JdbcTemplate jdbcTemplate() {
    DataSource dataSource = seedDataSource;
    if (dataSource == null) {
      dataSource =
          DataSourceBuilder.create()
              .url(DATABASE.getJdbcUrl())
              .username(DATABASE.getUsername())
              .password(DATABASE.getPassword())
              .build();
      seedDataSource = dataSource;
    }
    return new JdbcTemplate(dataSource);
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
