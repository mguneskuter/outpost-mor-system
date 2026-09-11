package com.outpost.framework.persistence.flyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class FxSeedIntegrationTest {

  private static final String[] CURRENCY_CODES = {
    "CZK", "DKK", "EUR", "GBP", "HUF", "PLN", "RON", "SEK", "USD"
  };

  private PostgreSQLContainer<?> database;

  @BeforeEach
  void migrateFreshDatabase() throws Exception {
    Path root = repositoryRoot();
    Process generator =
        new ProcessBuilder("java", root.resolve("local/fx/GenerateFxSeed.java").toString())
            .directory(root.toFile())
            .inheritIO()
            .start();
    assertThat(generator.waitFor()).isZero();
    database =
        PostgresTestDatabase.startContainer(
            "outpost_fx_seed", "outpost_fx_seed", "outpost_fx_seed");
    database.start();
    Flyway.configure()
        .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .schemas("public")
        .defaultSchema("public")
        .load()
        .migrate();
  }

  @AfterEach
  void stopDatabase() {
    database.stop();
  }

  @Test
  void loadsObservedAndReplayedValuesUnderTheirExplicitIds() throws Exception {
    try (Connection connection = database.createConnection("")) {
      insertCurrencies(connection);
      applySeed(connection);
      assertThat(count(connection, "fx_rate")).isEqualTo(864);
      assertThat(count(connection, "fx_fee")).isEqualTo(72);
      assertThat(queryLong(connection, "SELECT min(fx_rate_id) FROM fx_rate")).isEqualTo(1);
      assertThat(queryLong(connection, "SELECT max(fx_rate_id) FROM fx_rate")).isEqualTo(864);
      assertThat(count(connection, "SELECT DISTINCT fx_rate_id FROM fx_rate")).isEqualTo(864);
      assertThat(
              queryBigDecimal(
                  connection,
                  "SELECT rate FROM fx_rate WHERE base_currency_id = 3 AND quote_currency_id = 9"
                      + " AND rate_date = DATE '2026-09-07'"))
          .isEqualByComparingTo("1.1622");
      assertThat(
              queryBigDecimal(
                  connection,
                  "SELECT rate FROM fx_rate WHERE base_currency_id = 3 AND quote_currency_id = 9"
                      + " AND rate_date = DATE '2026-09-14'"))
          .isEqualByComparingTo("1.1622");
      assertThat(
              queryBigDecimal(
                  connection,
                  "SELECT rate FROM fx_rate WHERE base_currency_id = 3 AND quote_currency_id = 9"
                      + " AND rate_date = DATE '2026-09-12'"))
          .isEqualByComparingTo("1.1592");
    }
  }

  @Test
  void seedingAgainHasNoEffect() throws Exception {
    try (Connection connection = database.createConnection("")) {
      insertCurrencies(connection);
      applySeed(connection);
      applySeed(connection);
      assertThat(count(connection, "fx_rate")).isEqualTo(864);
      assertThat(count(connection, "fx_fee")).isEqualTo(72);
    }
  }

  @Test
  void divergentSeedRowFails() throws Exception {
    try (Connection connection = database.createConnection("")) {
      insertCurrencies(connection);
      applySeed(connection);
      execute(connection, "UPDATE fx_rate SET rate = 9 WHERE fx_rate_id = 1");
      assertThatThrownBy(() -> applySeed(connection)).isInstanceOf(Exception.class);
    }
  }

  @Test
  void generatedIdsDoNotCollideAfterSeed() throws Exception {
    try (Connection connection = database.createConnection("")) {
      insertCurrencies(connection);
      applySeed(connection);
      execute(
          connection,
          "INSERT INTO fx_rate (base_currency_id, quote_currency_id, rate_date, rate, source)"
              + " VALUES (3, 9, DATE '2026-09-20', 1.0, 'ECB')");
      assertThat(
              queryLong(
                  connection, "SELECT fx_rate_id FROM fx_rate WHERE rate_date = DATE '2026-09-20'"))
          .isEqualTo(865);
    }
  }

  private static void insertCurrencies(Connection connection) throws SQLException {
    for (int id = 1; id <= CURRENCY_CODES.length; id++) {
      execute(
          connection,
          "INSERT INTO currency VALUES (" + id + ", '" + CURRENCY_CODES[id - 1] + "', 2)");
    }
  }

  private static void applySeed(Connection connection) throws Exception {
    Path root = repositoryRoot();
    execute(connection, Files.readString(root.resolve("local/generated/fx/fx_rate.sql")));
    execute(connection, Files.readString(root.resolve("local/generated/fx/fx_fee.sql")));
  }

  private static Path repositoryRoot() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (directory != null && !Files.exists(directory.resolve("local/fx/GenerateFxSeed.java"))) {
      directory = directory.getParent();
    }
    return Objects.requireNonNull(directory);
  }

  private static long count(Connection connection, String tableOrQuery) throws SQLException {
    String query =
        tableOrQuery.startsWith("SELECT")
            ? "SELECT count(*) FROM (" + tableOrQuery + ") rows"
            : "SELECT count(*) FROM " + tableOrQuery;
    return queryLong(connection, query);
  }

  private static long queryLong(Connection connection, String sql) throws SQLException {
    try (var result = connection.createStatement().executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static BigDecimal queryBigDecimal(Connection connection, String sql) throws SQLException {
    try (var result = connection.createStatement().executeQuery(sql)) {
      result.next();
      return result.getBigDecimal(1);
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
