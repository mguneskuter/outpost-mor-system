package com.outpost.platform.staticdata.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = EnsureStaticDataJobApplication.class)
class SeedIntegrationTest {
  private static final String PSP_SIMULATOR_BASE_URL = "http://localhost:8081";
  private static final String PSP_SIMULATOR_API_KEY = "demo-outpost-api-key";
  private static final String PSP_SIMULATOR_HMAC_SECRET = "demo-hmac-secret";
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer("outpost_seed", "outpost_seed", "outpost_seed");

  static {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
  }

  private final JdbcTemplate jdbcTemplate;
  private final EnsureStaticDataJob staticDataJob;

  @Autowired
  SeedIntegrationTest(JdbcTemplate jdbcTemplate, EnsureStaticDataJob staticDataJob) {
    this.jdbcTemplate = jdbcTemplate;
    this.staticDataJob = staticDataJob;
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @BeforeEach
  void resetSeededRows() {
    jdbcTemplate.update("DELETE FROM tax_rate");
    jdbcTemplate.update("DELETE FROM register");
    jdbcTemplate.update("TRUNCATE TABLE account RESTART IDENTITY CASCADE");
    jdbcTemplate.update(
        "DELETE FROM account_type_register_type WHERE account_type_register_type_id = 100");
    staticDataJob.ensure();
  }

  @Test
  void seedsTheFixedAccountsAndTheirRegisters() {
    runSeed("account.sql");
    runSeed("register.sql");

    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT account_id, parent_account_id, code, name FROM account "
                    + "WHERE account_id = 1"))
        .containsEntry("account_id", 1L)
        .containsEntry("parent_account_id", null)
        .containsEntry("code", "ROOT")
        .containsEntry("name", "Outpost Chart Root");
    assertAccount(100L, 1L, "OUTPOST", "Outpost");
    assertAccount(200L, 1L, "DEMO_MERCHANT", "Demo Merchant");
    assertAccount(201L, 200L, "DEMO_MERCHANT_PAYOUT", "Demo Merchant Payout Account");
    assertAccount(210L, 1L, "DEMO_MERCHANT_2", "Demo Merchant 2");
    assertAccount(211L, 210L, "DEMO_MERCHANT_2_PAYOUT", "Demo Merchant 2 Payout Account");
    assertAccount(300L, 1L, "DEMO_PSP", "Demo PSP");
    runSeed("psp_configuration.sql");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT account_id FROM psp_configuration WHERE account_id = 300", Long.class))
        .isEqualTo(300L);
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT parent_account_id, code, name FROM account WHERE account_id = 1029"))
        .containsEntry("parent_account_id", 1L)
        .containsEntry("code", "TAX_AUTHORITY_US")
        .containsEntry("name", "United States Tax Authority");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT account_id, register_id, register_type_id FROM register "
                    + "ORDER BY account_id, register_type_id"))
        .containsExactlyElementsOf(
            jdbcTemplate.queryForList(
                "SELECT a.account_id, a.account_id * 100 + m.register_type_id AS register_id, "
                    + "m.register_type_id FROM account a "
                    + "JOIN account_type_register_type m ON m.account_type_id = a.account_type_id "
                    + "WHERE a.account_id <> 1 ORDER BY a.account_id, m.register_type_id"));
  }

  @Test
  void divergentTaxAuthorityAndFeeRowsFailWithoutOverwrite() {
    runPrerequisiteSeeds();
    jdbcTemplate.update("UPDATE tax_authority_account SET account_id = 1029 WHERE country_id = 1");
    jdbcTemplate.update(
        "UPDATE merchant_fee_configuration SET fee_rate_bps = 999 "
            + "WHERE account_id = 200 AND currency_id = "
            + "(SELECT currency_id FROM currency WHERE currency_code = 'EUR')");

    assertThatThrownBy(() -> runSeed("tax_authority_account.sql"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> runSeed("merchant_fee_configuration.sql"))
        .isInstanceOf(DataAccessException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT account_id FROM tax_authority_account WHERE country_id = 1", Long.class))
        .isEqualTo(1029L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT fee_rate_bps FROM merchant_fee_configuration WHERE account_id = 200 "
                    + "AND currency_id = (SELECT currency_id FROM currency "
                    + "WHERE currency_code = 'EUR')",
                Long.class))
        .isEqualTo(999L);
  }

  @Test
  void rerunningRestoresDeletedRegistersWithoutChangingAccounts() {
    runSeed("account.sql");
    runSeed("register.sql");
    final List<Map<String, Object>> accounts =
        jdbcTemplate.queryForList("SELECT * FROM account ORDER BY account_id");
    jdbcTemplate.update("DELETE FROM register WHERE account_id = 200 AND register_type_id = 1");

    runSeed("account.sql");
    runSeed("register.sql");

    assertThat(jdbcTemplate.queryForList("SELECT * FROM account ORDER BY account_id"))
        .containsExactlyElementsOf(accounts);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM register WHERE account_id = 200 AND register_type_id = 1",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void rerunningTaxRatesWritesNothing() {
    runSeed("tax_rate.sql");
    final List<Map<String, Object>> rates =
        jdbcTemplate.queryForList("SELECT * FROM tax_rate ORDER BY tax_rate_id");

    runSeed("tax_rate.sql");

    assertThat(rates).isNotEmpty();
    assertThat(jdbcTemplate.queryForList("SELECT * FROM tax_rate ORDER BY tax_rate_id"))
        .containsExactlyElementsOf(rates);
  }

  @Test
  void failsOnDivergentTaxRateWithoutOverwritingIt() {
    runSeed("tax_rate.sql");
    jdbcTemplate.update("UPDATE tax_rate SET rate = 0.5 WHERE tax_rate_id = 1");

    assertThatThrownBy(() -> runSeed("tax_rate.sql")).isInstanceOf(DataAccessException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT rate FROM tax_rate WHERE tax_rate_id = 1", BigDecimal.class))
        .isEqualByComparingTo("0.5");
  }

  @Test
  void failsOnDivergentAccountWithoutOverwritingIt() {
    jdbcTemplate.update(
        "INSERT INTO account (account_id, account_type_id, parent_account_id, code, name, "
            + "is_active, created_ts) VALUES (1, 1, NULL, 'ROOT', 'Wrong', TRUE, "
            + "'2026-01-01 00:00:00+00')");

    assertThatThrownBy(() -> runSeed("account.sql")).isInstanceOf(DataAccessException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT name FROM account WHERE account_id = 1", String.class))
        .isEqualTo("Wrong");
  }

  @Test
  void addsRegisterForRecentlyAddedMapping() {
    jdbcTemplate.update("INSERT INTO account_type_register_type VALUES (100, 2, 2)");

    runSeed("account.sql");
    runSeed("register.sql");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT register_id FROM register WHERE account_id = 200 AND register_type_id = 2",
                Long.class))
        .isEqualTo(20002L);
  }

  @Test
  void advancesSequencesPastSeededIds() {
    runSeed("account.sql");
    runSeed("register.sql");

    assertThat(
            jdbcTemplate.queryForObject(
                "INSERT INTO account (account_type_id, code, name, is_active, created_ts) "
                    + "VALUES (1, 'NEXT_ACCOUNT', 'Next Account', TRUE, "
                    + "'2026-01-01 00:00:00+00') RETURNING account_id",
                Long.class))
        .isGreaterThan(
            1000L
                + Objects.requireNonNull(
                    jdbcTemplate.queryForObject(
                        "SELECT MAX(country_id) FROM country", Long.class)));
    assertThat(
            jdbcTemplate.queryForObject(
                "INSERT INTO register (account_id, register_type_id) VALUES (1, 1) "
                    + "RETURNING register_id",
                Long.class))
        .isGreaterThan(30000L);
  }

  private void runSeed(String fileName) {
    String script;
    try {
      script = Files.readString(seedPath(fileName), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    String jdbcScript =
        script
            .replace(":'psp_simulator_base_url'", "'" + PSP_SIMULATOR_BASE_URL + "'")
            .replace(":'psp_simulator_api_key'", "'" + PSP_SIMULATOR_API_KEY + "'")
            .replace(":'psp_simulator_hmac_secret'", "'" + PSP_SIMULATOR_HMAC_SECRET + "'");
    jdbcTemplate.execute(
        (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
            (Connection connection) -> {
              try (var statement = connection.createStatement()) {
                statement.execute(jdbcScript);
              }
              return true;
            });
  }

  private void runPrerequisiteSeeds() {
    runSeed("account.sql");
    runSeed("register.sql");
    runSeed("tax_authority_account.sql");
    runSeed("merchant_fee_configuration.sql");
  }

  private void assertAccount(long accountId, long parentAccountId, String code, String name) {
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT parent_account_id, code, name FROM account WHERE account_id = ?",
                accountId))
        .containsEntry("parent_account_id", parentAccountId)
        .containsEntry("code", code)
        .containsEntry("name", name);
  }

  private static Path seedPath(String fileName) {
    Path migration = Path.of(migrationLocation()).toAbsolutePath();
    Path db = Objects.requireNonNull(migration.getParent());
    Path outpost = Objects.requireNonNull(db.getParent());
    Path repository = Objects.requireNonNull(outpost.getParent());
    return repository.resolve("local").resolve("seed_data").resolve(fileName);
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
