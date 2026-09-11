package com.outpost.framework.persistence.flyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class AccountingSchemaIntegrationTest {

  private PostgreSQLContainer<?> database;

  @BeforeEach
  void migrateFreshDatabase() {
    database =
        PostgresTestDatabase.startContainer(
            "outpost_accounting", "outpost_accounting", "outpost_accounting");
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
  void acceptsValidRowsAndEnforcesAccountFeeAndPaymentDetailConstraints() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      execute(connection, "INSERT INTO register_type VALUES (1, 'MERCHANT_PAYABLE')");
      execute(connection, "INSERT INTO transaction_type VALUES (1, 'PAYMENT')");
      execute(
          connection,
          "INSERT INTO transaction_event_type VALUES "
              + "(1, 'ORDER_CREATED', true), (5, 'CAPTURED', true)");
      execute(connection, "INSERT INTO journal_entry_type VALUES (1, 'CAPTURE')");
      execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT'), (3, 'PSP')");
      execute(
          connection,
          "INSERT INTO fee_mode VALUES (1, 'PERCENTAGE'), (2, 'PERCENTAGE_PLUS_FIXED')");
      execute(connection, "INSERT INTO currency VALUES (1, 'EUR', 2)");
      execute(connection, "INSERT INTO country VALUES (1, 'NL', 'Netherlands')");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (100, 2, 'merchant', 'Merchant', true, now()), "
              + "(101, 3, 'psp', 'PSP', true, now())");
      execute(connection, "INSERT INTO register (account_id, register_type_id) VALUES (100, 1)");
      execute(
          connection,
          "INSERT INTO merchant_fee_configuration "
              + "(account_id, account_type_id, currency_id, fee_mode_id, fee_rate_bps, fee_fixed) "
              + "VALUES (100, 2, 1, 1, 100, NULL)");
      execute(
          connection,
          "INSERT INTO transaction "
              + "(transaction_id, transaction_type_id, account_id, reference, quantity, "
              + "currency_id, created_ts) VALUES (200, 1, 100, 'ref', 1000, 1, now())");
      execute(
          connection,
          "INSERT INTO payment_detail "
              + "(transaction_id, transaction_type_id, shopper_country_id, psp_account_id, "
              + "net_quantity, tax_quantity) VALUES (200, 1, 1, 101, 900, 100)");
      connection.commit();
      execute(
          connection,
          "INSERT INTO transaction (transaction_id, transaction_type_id, account_id, "
              + "reference, quantity, currency_id, created_ts) VALUES "
              + "(201, 1, 100, 'ref-2', 1000, 1, now())");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO payment_detail "
                          + "(transaction_id, transaction_type_id, shopper_country_id, "
                          + "psp_account_id, net_quantity, tax_quantity) "
                          + "VALUES (201, 1, 1, 100, 900, 100)"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(
                      connection, "UPDATE account SET account_type_id = 2 WHERE account_id = 101"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void rejectsDuplicateAccountCodesAndRegisters() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT'), (3, 'PSP')");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (100, 2, 'same', 'A', true, now())");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO account (account_id, account_type_id, code, name, is_active, "
                          + "created_ts) VALUES (101, 3, 'same', 'B', true, now())"))
          .isInstanceOf(SQLException.class);
      execute(connection, "INSERT INTO register_type VALUES (1, 'MERCHANT_PAYABLE')");
      execute(connection, "INSERT INTO register (account_id, register_type_id) VALUES (100, 1)");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO register (account_id, register_type_id) VALUES (100, 1)"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void enforcesAccountTypeRegisterTypeMappingConstraints() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      assertThat(queryLong(connection, "SELECT COUNT(*) FROM account_type_register_type")).isZero();
      execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT')");
      execute(
          connection,
          "INSERT INTO register_type VALUES " + "(1, 'MERCHANT_PAYABLE'), (8, 'PENDING_FEE')");
      execute(
          connection, "INSERT INTO account_type_register_type VALUES " + "(1, 2, 1), (2, 2, 8)");

      assertThatThrownBy(
              () -> execute(connection, "INSERT INTO account_type_register_type VALUES (3, 2, 1)"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () -> execute(connection, "INSERT INTO account_type_register_type VALUES (3, 99, 1)"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () -> execute(connection, "INSERT INTO account_type_register_type VALUES (3, 2, 99)"))
          .isInstanceOf(SQLException.class);

      assertThat(
              queryLong(
                  connection,
                  "SELECT COUNT(*) FROM account_type_register_type WHERE account_type_id = 2"))
          .isEqualTo(2);
      assertThat(
              queryString(
                  connection,
                  "SELECT column_default FROM information_schema.columns "
                      + "WHERE table_schema = 'public' "
                      + "AND table_name = 'account_type_register_type' "
                      + "AND column_name = 'account_type_register_type_id'"))
          .isNull();
      assertThat(
              queryString(
                  connection,
                  "SELECT pg_get_serial_sequence("
                      + "'public.account_type_register_type', "
                      + "'account_type_register_type_id')"))
          .isNull();
    }
  }

  @Test
  void enforcesAppendOnlyBalanceAndRequiredEntryCouplingAtCommit() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      setupJournalFixture(connection);
      execute(connection, "INSERT INTO transaction_event VALUES (300, 200, 5, now())");
      execute(connection, "INSERT INTO journal_entry VALUES (400, 300, 1, now(), now())");
      execute(
          connection,
          "INSERT INTO journal_entry_line VALUES (401, 400, 300, 1, 10), (402, 400, 300, 1, -10)");
      connection.commit();
      assertThatThrownBy(() -> execute(connection, "UPDATE journal_entry SET booked = now()"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(() -> execute(connection, "DELETE FROM journal_entry_line"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      execute(connection, "INSERT INTO transaction_event VALUES (301, 200, 5, now())");
      assertThatThrownBy(connection::commit).isInstanceOf(SQLException.class);
      connection.rollback();
      execute(connection, "INSERT INTO transaction_event VALUES (301, 200, 5, now())");
      execute(connection, "INSERT INTO journal_entry VALUES (401, 301, 1, now(), now())");
      execute(
          connection,
          "INSERT INTO journal_entry_line VALUES (403, 401, 300, 1, 10), (404, 401, 300, 1, -10)");
      connection.commit();
    }
  }

  @Test
  void orderCreatedEventRequiresBalancedJournalEntryAtCommit() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      setupJournalFixture(connection);
      execute(connection, "INSERT INTO register_type VALUES (8, 'PENDING_FEE')");
      execute(connection, "INSERT INTO journal_entry_type VALUES (3, 'FEE_PENDING')");
      execute(connection, "INSERT INTO account_type VALUES (6, 'PLATFORM')");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (110, 6, 'platform', 'Platform', true, now())");
      execute(
          connection,
          "INSERT INTO register (register_id, account_id, register_type_id) VALUES "
              + "(310, 100, 8), (311, 110, 8)");
      connection.commit();

      execute(connection, "INSERT INTO transaction_event VALUES (320, 200, 1, now())");
      execute(connection, "INSERT INTO journal_entry VALUES (420, 320, 3, now(), now())");
      execute(
          connection,
          "INSERT INTO journal_entry_line VALUES "
              + "(420, 420, 310, 1, 10), (421, 420, 311, 1, -10)");
      connection.commit();

      assertThatThrownBy(
              () -> {
                execute(connection, "INSERT INTO transaction_event VALUES (321, 200, 1, now())");
                connection.commit();
              })
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void rejectsUnbalancedEntriesButAllowsBalancedMultiCurrencyEntries() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      setupJournalFixture(connection);
      execute(connection, "INSERT INTO currency VALUES (2, 'USD', 2)");
      connection.commit();

      execute(connection, "INSERT INTO transaction_event VALUES (305, 200, 2, now())");
      connection.commit();
      assertThatThrownBy(
              () -> {
                execute(
                    connection,
                    "UPDATE transaction_event_type SET requires_journal_entry = true "
                        + "WHERE transaction_event_type_id = 2");
                connection.commit();
              })
          .isInstanceOf(SQLException.class);
      connection.rollback();

      assertThatThrownBy(
              () -> {
                execute(connection, "INSERT INTO transaction_event VALUES (302, 200, 5, now())");
                execute(connection, "INSERT INTO journal_entry VALUES (402, 302, 1, now(), now())");
                execute(
                    connection,
                    "INSERT INTO journal_entry_line VALUES "
                        + "(405, 402, 300, 1, 10), (406, 402, 300, 1, -9)");
                connection.commit();
              })
          .isInstanceOf(SQLException.class);
      connection.rollback();

      execute(connection, "INSERT INTO transaction_event VALUES (303, 200, 5, now())");
      execute(connection, "INSERT INTO journal_entry VALUES (403, 303, 1, now(), now())");
      execute(
          connection,
          "INSERT INTO journal_entry_line VALUES "
              + "(407, 403, 300, 1, 10), (408, 403, 300, 1, -10), "
              + "(409, 403, 300, 2, 20), (410, 403, 300, 2, -20)");
      connection.commit();

      assertThatThrownBy(
              () -> {
                execute(connection, "INSERT INTO transaction_event VALUES (304, 200, 2, now())");
                execute(connection, "INSERT INTO journal_entry VALUES (404, 304, 1, now(), now())");
                connection.commit();
              })
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  private void setupJournalFixture(Connection connection) throws SQLException {
    connection.setAutoCommit(false);
    execute(connection, "INSERT INTO register_type VALUES (1, 'MERCHANT_PAYABLE')");
    execute(connection, "INSERT INTO transaction_type VALUES (1, 'PAYMENT')");
    execute(
        connection,
        "INSERT INTO transaction_event_type VALUES "
            + "(1, 'ORDER_CREATED', true), (2, 'AUTHORISED', false), (5, 'CAPTURED', true)");
    execute(connection, "INSERT INTO journal_entry_type VALUES (1, 'CAPTURE')");
    execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT')");
    execute(connection, "INSERT INTO currency VALUES (1, 'EUR', 2)");
    execute(
        connection,
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "VALUES (100, 2, 'merchant', 'Merchant', true, now())");
    execute(
        connection,
        "INSERT INTO register (register_id, account_id, register_type_id) VALUES (300, 100, 1)");
    execute(
        connection,
        "INSERT INTO transaction "
            + "(transaction_id, transaction_type_id, account_id, reference, quantity, "
            + "currency_id, created_ts) VALUES (200, 1, 100, 'ref', 100, 1, now())");
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static long queryLong(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        var resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private static String queryString(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        var resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getString(1);
    }
  }
}
