package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Startup failures that only occur against a real database: divergent reference data, invalid tax
 * data, and the Ledger signing secret, which is read only once a database connection is available.
 * {@link GatewayApiStartupFailureTest} covers the database-free cases.
 */
class GatewayApiStartupFailureIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_startup", "outpost_gateway_startup", "outpost_gateway_startup");

  static {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
  }

  @BeforeEach
  void clearReferenceData() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    jdbcTemplate.update("DELETE FROM tax_rate");
    jdbcTemplate.update("DELETE FROM country_subdivision");
    jdbcTemplate.update("DELETE FROM country");
    jdbcTemplate.update("DELETE FROM currency");
    jdbcTemplate.update("DELETE FROM product_type");
    jdbcTemplate.update("DELETE FROM account_type");
    jdbcTemplate.update("DELETE FROM fee_mode");
  }

  @Test
  void failsClosedOnUnknownTaxCountryCode() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    jdbcTemplate.update(
        "INSERT INTO country (country_id, iso_code, name) VALUES (999, 'ZZ', 'Unrecognised')");
    jdbcTemplate.update("INSERT INTO tax_rate (country_id, rate) VALUES (999, 0.10)");

    assertThatThrownBy(this::startAgainstDatabase)
        .isInstanceOf(Exception.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsClosedOnDivergentReferenceData() {
    JdbcTemplate jdbcTemplate = jdbcTemplate();
    GatewayStaticDataFixtures.materializeAll(jdbcTemplate);
    jdbcTemplate.update("UPDATE account_type SET code = 'WRONG' WHERE account_type_id = 1");

    assertThatThrownBy(this::startAgainstDatabase)
        .isInstanceOf(Exception.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsClosedWhenLedgerSigningSecretIsAbsent() {
    GatewayStaticDataFixtures.materializeAll(jdbcTemplate());

    assertThatThrownBy(() -> startAgainstDatabase(/* ledgerHmacSecret= */ null))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining("hmacSecret");
  }

  @Test
  void failsClosedWhenLedgerSigningSecretIsBlank() {
    GatewayStaticDataFixtures.materializeAll(jdbcTemplate());

    assertThatThrownBy(() -> startAgainstDatabase(""))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining("hmacSecret");
  }

  @ParameterizedTest
  @MethodSource("invalidOperationalSettings")
  void failsClosedForInvalidOperationalSetting(String property, String value, String fieldName) {
    GatewayStaticDataFixtures.materializeAll(jdbcTemplate());

    assertThatThrownBy(() -> startAgainstDatabase("integration-ledger-key", property, value))
        .isInstanceOf(Exception.class)
        .rootCause()
        .hasMessageContaining(fieldName);
  }

  private static Stream<Arguments> invalidOperationalSettings() {
    return Stream.of(
        Arguments.of("outpost.gateway.ledger.connect-timeout", "PT0S", "connectTimeout"),
        Arguments.of("outpost.gateway.ledger.connect-timeout", "-PT1S", "connectTimeout"),
        Arguments.of("outpost.gateway.ledger.read-timeout", "PT6M", "readTimeout"),
        Arguments.of("outpost.gateway.ledger.base-url", "not-a-url", "baseUrl"));
  }

  private void startAgainstDatabase() {
    startAgainstDatabase("integration-ledger-key");
  }

  private void startAgainstDatabase(@Nullable String ledgerHmacSecret) {
    startAgainstDatabase(ledgerHmacSecret, null, null);
  }

  private void startAgainstDatabase(
      @Nullable String ledgerHmacSecret, @Nullable String property, @Nullable String value) {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("spring.datasource.url", DATABASE.getJdbcUrl());
    env.setProperty("spring.datasource.username", DATABASE.getUsername());
    env.setProperty("spring.datasource.password", DATABASE.getPassword());
    env.setProperty("OUTPOST_HMAC_ENCRYPTION_KEY", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    if (ledgerHmacSecret != null) {
      env.setProperty("OUTPOST_LEDGER_GATEWAY_HMAC_SECRET", ledgerHmacSecret);
    }
    if (property != null && value != null) {
      env.setProperty(property, value);
    }
    new SpringApplicationBuilder(GatewayApiApplication.class)
        .web(WebApplicationType.NONE)
        .environment(env)
        .run();
  }

  // Built once and reused: DataSourceBuilder produces a pooled datasource, and this class starts
  // one Spring context per test, so a fresh instance per call would leak a connection pool per
  // call rather than per test.
  private static final DataSource TEST_ASSERTION_DATA_SOURCE =
      DataSourceBuilder.create()
          .url(DATABASE.getJdbcUrl())
          .username(DATABASE.getUsername())
          .password(DATABASE.getPassword())
          .build();

  private static JdbcTemplate jdbcTemplate() {
    return new JdbcTemplate(TEST_ASSERTION_DATA_SOURCE);
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
