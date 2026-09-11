package com.outpost.framework.persistence.flyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class FxSchemaIntegrationTest {

  private PostgreSQLContainer<?> database;

  @BeforeEach
  void migrateFreshDatabase() {
    database =
        PostgresTestDatabase.startContainer(
            "outpost_fx_schema", "outpost_fx_schema", "outpost_fx_schema");
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
  void acceptsValidRowsAndAdvancesIndependentSequences() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      execute(connection, "INSERT INTO currency VALUES (3, 'EUR', 2), (9, 'USD', 2)");
      execute(
          connection,
          "INSERT INTO fx_rate (fx_rate_id, base_currency_id, quote_currency_id, rate_date,"
              + " rate, source)"
              + " VALUES (10, 3, 9, DATE '2026-09-07', 1.2500000000, 'ECB')");
      execute(
          connection,
          "INSERT INTO fx_fee (fx_fee_id, base_currency_id, quote_currency_id, fee_rate_bps)"
              + " VALUES (20, 3, 9, 100)");
      execute(connection, "SELECT setval('fx_rate_seq', 10, true), setval('fx_fee_seq', 20, true)");
      try (var rates = connection.createStatement().executeQuery("SELECT nextval('fx_rate_seq')")) {
        rates.next();
        assertThat(rates.getLong(1)).isEqualTo(11);
      }
      try (var fees = connection.createStatement().executeQuery("SELECT nextval('fx_fee_seq')")) {
        fees.next();
        assertThat(fees.getLong(1)).isEqualTo(21);
      }
    }
  }

  @Test
  void rejectsInvalidPairsRatesFeesAndDuplicates() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      execute(connection, "INSERT INTO currency VALUES (3, 'EUR', 2), (9, 'USD', 2)");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO fx_rate (base_currency_id, quote_currency_id, rate_date,"
                          + " rate, source)"
                          + " VALUES (3, 3, DATE '2026-09-07', 1, 'ECB')"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO fx_rate (base_currency_id, quote_currency_id, rate_date,"
                          + " rate, source)"
                          + " VALUES (3, 9, DATE '2026-09-07', 0, 'ECB')"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO fx_fee (base_currency_id, quote_currency_id, fee_rate_bps)"
                          + " VALUES (3, 3, 0)"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO fx_fee (base_currency_id, quote_currency_id, fee_rate_bps)"
                          + " VALUES (3, 9, -1)"))
          .isInstanceOf(SQLException.class);
      execute(
          connection,
          "INSERT INTO fx_fee (base_currency_id, quote_currency_id, fee_rate_bps)"
              + " VALUES (3, 9, 100)");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO fx_fee (base_currency_id, quote_currency_id, fee_rate_bps)"
                          + " VALUES (3, 9, 100)"))
          .isInstanceOf(SQLException.class);
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
