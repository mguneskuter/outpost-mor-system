package com.outpost.framework.persistence.flyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.account.AccountTypes;
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
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE payment_detail SET tax_quantity = 101 WHERE transaction_id = 200"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () -> execute(connection, "DELETE FROM payment_detail WHERE transaction_id = 200"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
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
  void merchantFeeAndPaymentDetailConstraintsResolveByCodeRegardlessOfSeededIdentifiers()
      throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      execute(connection, "INSERT INTO account_type VALUES (20, 'MERCHANT'), (30, 'PSP')");
      execute(
          connection,
          "INSERT INTO fee_mode VALUES (10, 'PERCENTAGE'), (11, 'PERCENTAGE_PLUS_FIXED')");
      execute(connection, "INSERT INTO transaction_type VALUES (40, 'PAYMENT'), (41, 'CAPTURE')");
      execute(connection, "INSERT INTO currency VALUES (1, 'EUR', 2)");
      execute(connection, "INSERT INTO country VALUES (1, 'NL', 'Netherlands')");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (100, 20, 'merchant', 'Merchant', true, now()), "
              + "(101, 30, 'psp', 'PSP', true, now())");
      execute(
          connection,
          "INSERT INTO merchant_fee_configuration "
              + "(account_id, account_type_id, currency_id, fee_mode_id, fee_rate_bps, fee_fixed) "
              + "VALUES (100, 20, 1, 10, 100, NULL)");
      execute(
          connection,
          "INSERT INTO transaction "
              + "(transaction_id, transaction_type_id, account_id, reference, quantity, "
              + "currency_id, created_ts) VALUES (200, 40, 100, 'ref', 1000, 1, now())");
      execute(
          connection,
          "INSERT INTO payment_detail "
              + "(transaction_id, transaction_type_id, shopper_country_id, psp_account_id, "
              + "net_quantity, tax_quantity) VALUES (200, 40, 1, 101, 900, 100)");
      connection.commit();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_fee_configuration "
                          + "(account_id, account_type_id, currency_id, fee_mode_id, "
                          + "fee_rate_bps, fee_fixed) VALUES (101, 30, 1, 10, 100, NULL)"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE merchant_fee_configuration SET fee_mode_id = 11 "
                          + "WHERE account_id = 100"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE merchant_fee_configuration SET fee_fixed = 50 "
                          + "WHERE account_id = 100"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      execute(
          connection,
          "INSERT INTO transaction "
              + "(transaction_id, transaction_type_id, account_id, reference, quantity, "
              + "currency_id, created_ts) VALUES (201, 41, 100, 'ref-2', 1000, 1, now())");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO payment_detail "
                          + "(transaction_id, transaction_type_id, shopper_country_id, "
                          + "psp_account_id, net_quantity, tax_quantity) "
                          + "VALUES (201, 41, 1, 101, 900, 100)"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void refundDetailRequiresRefundTransactionTypeRegardlessOfSeededIdentifiers()
      throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      execute(connection, "INSERT INTO account_type VALUES (20, 'MERCHANT')");
      execute(connection, "INSERT INTO transaction_type VALUES (40, 'PAYMENT'), (41, 'REFUND')");
      execute(connection, "INSERT INTO currency VALUES (1, 'EUR', 2)");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (100, 20, 'merchant', 'Merchant', true, now())");
      execute(
          connection,
          "INSERT INTO transaction "
              + "(transaction_id, transaction_type_id, account_id, reference, quantity, "
              + "currency_id, created_ts) VALUES "
              + "(200, 40, 100, 'payment-ref', 1000, 1, now()), "
              + "(201, 41, 100, 'refund-ref', 200, 1, now())");
      execute(
          connection,
          "INSERT INTO refund_detail (transaction_id, transaction_type_id, net_quantity, "
              + "tax_quantity) VALUES (201, 41, 180, 20)");
      connection.commit();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO refund_detail (transaction_id, transaction_type_id, "
                          + "net_quantity, tax_quantity) VALUES (200, 40, 180, 20)"))
          .isInstanceOf(SQLException.class);
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
  void taxAuthorityAccountRequiresTaxAuthorityAndProtectsAccountType() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT'), (5, 'TAX_AUTHORITY')");
      execute(connection, "INSERT INTO country VALUES (1, 'NL', 'Netherlands')");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (100, 2, 'merchant', 'Merchant', true, now()), "
              + "(101, 5, 'tax-authority', 'Tax Authority', true, now())");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO tax_authority_account (country_id, account_id, account_type_id) "
                          + "VALUES (1, 100, 2)"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT'), (5, 'TAX_AUTHORITY')");
      execute(connection, "INSERT INTO country VALUES (1, 'NL', 'Netherlands')");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (100, 2, 'merchant', 'Merchant', true, now()), "
              + "(101, 5, 'tax-authority', 'Tax Authority', true, now())");
      execute(
          connection,
          "INSERT INTO tax_authority_account (country_id, account_id, account_type_id) "
              + "VALUES (1, 101, 5)");
      connection.commit();
      assertThatThrownBy(
              () ->
                  execute(
                      connection, "UPDATE account SET account_type_id = 2 WHERE account_id = 101"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
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
  void rejectsPspConfigurationForNonPspAccount() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      seedAccountTypes(connection);
      execute(
          connection,
          "INSERT INTO account (account_type_id, code, name, is_active, created_ts) "
              + "SELECT account_type_id, 'merchant', 'Merchant', true, now() "
              + "FROM account_type WHERE code = 'MERCHANT'");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO psp_configuration "
                          + "(account_id, account_type_id, base_url, api_key, hmac_secret) "
                          + "SELECT account_id, account_type_id, 'http://simulator', 'key', 'hmac' "
                          + "FROM account WHERE code = 'merchant'"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void enforcesPspEventQueueKeysReferencesUniquenessAndCompletionRules() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      execute(connection, "INSERT INTO psp_event_code VALUES (1, 'AUTHORISATION')");
      execute(connection, "INSERT INTO psp_event_status VALUES (1, 'RECEIVED'), (3, 'DONE')");
      execute(connection, "INSERT INTO psp_event_result VALUES (1, 'SUCCESS')");
      execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT'), (4, 'PSP')");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (100, 2, 'merchant', 'Merchant', true, now()), "
              + "(101, 4, 'psp', 'PSP', true, now())");

      execute(
          connection,
          "INSERT INTO psp_event_queue "
              + "(created_ts, status_id, type_id, reference, original_reference, account_id, "
              + "account_type_id, psp_account_id, psp_account_type_id, payload) VALUES "
              + "(now(), 1, 1, 'event-1', 'payment-1', 100, 2, 101, 4, '{}'::jsonb)");
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO psp_event_queue "
                          + "(created_ts, status_id, type_id, reference, original_reference, "
                          + "account_id, account_type_id, psp_account_id, psp_account_type_id, "
                          + "payload) VALUES "
                          + "(now(), 1, 1, 'event-1', 'payment-2', 100, 2, 101, 4, '{}'::jsonb)"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO psp_event_queue "
                          + "(created_ts, status_id, type_id, reference, original_reference, "
                          + "account_id, account_type_id, psp_account_id, psp_account_type_id, "
                          + "payload) VALUES "
                          + "(now(), 1, 1, 'event-2', 'payment-2', 101, 2, 101, 4, '{}'::jsonb)"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO psp_event_queue "
                          + "(created_ts, status_id, type_id, reference, original_reference, "
                          + "account_id, account_type_id, psp_account_id, psp_account_type_id, "
                          + "payload) VALUES "
                          + "(now(), 3, 1, 'event-3', 'payment-3', 100, 2, 101, 4, '{}'::jsonb)"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE psp_event_queue SET done = true WHERE reference = 'event-1'"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE psp_event_queue SET done_ts = now() WHERE reference = 'event-1'"))
          .isInstanceOf(SQLException.class);

      execute(
          connection,
          "UPDATE psp_event_queue SET status_id = 3, done = true, done_ts = now(), result_id = 1 "
              + "WHERE reference = 'event-1'");
      assertThat(queryLong(connection, "SELECT COUNT(*) FROM psp_event_queue")).isEqualTo(1);
    }
  }

  private void seedAccountTypes(Connection connection) throws SQLException {
    for (AccountTypes accountType : AccountTypes.values()) {
      execute(
          connection,
          "INSERT INTO account_type (account_type_id, code) VALUES (%d, '%s')"
              .formatted(
                  accountType.getValue().getAccountTypeId(), accountType.getValue().getCode()));
    }
  }

  @Test
  void rejectsAccountingEvidenceMutationAndDuplicateReferences() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      setupJournalFixture(connection);
      connection.commit();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE transaction SET quantity = 101 WHERE transaction_id = 200"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () -> execute(connection, "DELETE FROM transaction WHERE transaction_id = 200"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO transaction (transaction_id, transaction_type_id, account_id, "
                          + "reference, quantity, currency_id, created_ts) "
                          + "VALUES (201, 1, 100, 'ref', 100, 1, now())"))
          .isInstanceOf(SQLException.class);
      connection.rollback();

      execute(connection, "INSERT INTO transaction_event VALUES (300, 200, 2, now())");
      connection.commit();
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE transaction_event SET event_ts = now() "
                          + "WHERE transaction_event_id = 300"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(connection, "INSERT INTO transaction_event VALUES (301, 200, 2, now())"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(
                      connection, "DELETE FROM transaction_event WHERE transaction_event_id = 300"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void accountOnlyAllowsChangingActiveState() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT'), (3, 'PSP')");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, parent_account_id, code, name, "
              + "is_active, created_ts) "
              + "VALUES (100, 2, NULL, 'merchant', 'Merchant', true, '2026-01-01T00:00:00Z')");
      execute(connection, "UPDATE account SET is_active = false WHERE account_id = 100");
      connection.commit();
      assertThatThrownBy(
              () ->
                  execute(connection, "UPDATE account SET account_id = 101 WHERE account_id = 100"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(
                      connection, "UPDATE account SET account_type_id = 3 WHERE account_id = 100"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE account SET parent_account_id = 100 WHERE account_id = 100"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(connection, "UPDATE account SET code = 'changed' WHERE account_id = 100"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(connection, "UPDATE account SET name = 'Changed' WHERE account_id = 100"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "UPDATE account SET created_ts = '2026-01-02T00:00:00Z' "
                          + "WHERE account_id = 100"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
      assertThatThrownBy(() -> execute(connection, "DELETE FROM account WHERE account_id = 100"))
          .isInstanceOf(SQLException.class);
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
      execute(connection, "INSERT INTO transaction_event VALUES (301, 200, 6, now())");
      assertThatThrownBy(connection::commit).isInstanceOf(SQLException.class);
      connection.rollback();
      execute(connection, "INSERT INTO transaction_event VALUES (301, 200, 6, now())");
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
                execute(connection, "INSERT INTO transaction_event VALUES (321, 200, 6, now())");
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

      execute(connection, "INSERT INTO transaction_event VALUES (303, 200, 6, now())");
      execute(connection, "INSERT INTO journal_entry VALUES (403, 303, 1, now(), now())");
      execute(
          connection,
          "INSERT INTO journal_entry_line VALUES "
              + "(407, 403, 300, 1, 10), (408, 403, 300, 1, -10), "
              + "(409, 403, 300, 2, 20), (410, 403, 300, 2, -20)");
      connection.commit();

      assertThatThrownBy(
              () -> {
                execute(connection, "INSERT INTO transaction_event VALUES (304, 200, 7, now())");
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
            + "(1, 'ORDER_CREATED', true), (2, 'AUTHORISED', false), (5, 'CAPTURED', true), "
            + "(6, 'SETTLED', true), (7, 'VOIDED', false)");
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
