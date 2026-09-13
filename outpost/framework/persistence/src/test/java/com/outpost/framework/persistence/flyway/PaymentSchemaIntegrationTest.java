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

class PaymentSchemaIntegrationTest {

  private PostgreSQLContainer<?> database;

  @BeforeEach
  void migrateFreshDatabase() {
    database =
        PostgresTestDatabase.startContainer(
            "outpost_payment", "outpost_payment", "outpost_payment");
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
  void acceptsValidOrderRowsAndEnforcesKeysAndCompositeAccountTypes() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      seedReferenceData(connection);
      execute(
          connection,
          "INSERT INTO shopper_detail (shopper_id, email, full_name, country_id) "
              + "VALUES (1, 'shopper@example.com', 'Shopper', 1)");
      execute(
          connection,
          "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, "
              + "account_id, account_type_id, shopper_id, currency_id, net_amount, tax_amount, "
              + "gross_amount, idempotency_key, payment_reference, psp_account_id, "
              + "shopper_country_id, request_fingerprint, created_ts) "
              + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, 1210, "
              + "'idem-key', 'payment-ref', 101, 1, 'fingerprint', now())");
      execute(
          connection,
          "INSERT INTO order_item (order_item_id, order_id, product_type_id, "
              + "order_line_reference, merchant_line_reference, net_amount, tax_amount, "
              + "tax_rate) VALUES (1, 1, 1, 'line-ref', 'merchant-line-ref', 1000, 210, "
              + "0.2100)");
      connection.commit();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_order (order_id, order_reference, "
                          + "merchant_reference, account_id, account_type_id, shopper_id, "
                          + "currency_id, net_amount, tax_amount, gross_amount, "
                          + "idempotency_key, payment_reference, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (2, 'order-ref-2', 'merchant-ref-2', 101, 4, 1, 1, 1000, "
                          + "210, 1210, 'idem-key-2', 'payment-ref-2', 101, 1, "
                          + "'fingerprint', now())"))
          .isInstanceOf(SQLException.class);
      connection.rollback();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_order (order_id, order_reference, "
                          + "merchant_reference, account_id, account_type_id, shopper_id, "
                          + "currency_id, net_amount, tax_amount, gross_amount, "
                          + "idempotency_key, payment_reference, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (3, 'order-ref-3', 'merchant-ref-3', 100, 2, 1, 1, 1000, "
                          + "210, 1210, 'idem-key-3', 'payment-ref-3', 100, 1, "
                          + "'fingerprint', now())"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void rejectsDuplicateOrderKeysAndReferencesAndUnbalancedTotals() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      seedReferenceData(connection);
      execute(
          connection,
          "INSERT INTO shopper_detail (shopper_id, email, full_name, country_id) "
              + "VALUES (1, 'shopper@example.com', 'Shopper', 1)");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_order (order_id, order_reference, "
                          + "merchant_reference, account_id, account_type_id, shopper_id, "
                          + "currency_id, net_amount, tax_amount, gross_amount, "
                          + "idempotency_key, payment_reference, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, "
                          + "9999, 'idem-key', 'payment-ref', 101, 1, 'fingerprint', now())"))
          .isInstanceOf(SQLException.class);

      execute(
          connection,
          "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, "
              + "account_id, account_type_id, shopper_id, currency_id, net_amount, "
              + "tax_amount, gross_amount, idempotency_key, payment_reference, psp_account_id, "
              + "shopper_country_id, request_fingerprint, created_ts) "
              + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, 1210, "
              + "'idem-key', 'payment-ref', 101, 1, 'fingerprint', now())");
      execute(
          connection,
          "INSERT INTO order_item (order_item_id, order_id, product_type_id, "
              + "order_line_reference, merchant_line_reference, net_amount, tax_amount, "
              + "tax_rate) VALUES (1, 1, 1, 'line-ref', 'merchant-line-ref', 1000, 210, "
              + "0.2100)");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO order_item (order_item_id, order_id, "
                          + "product_type_id, order_line_reference, merchant_line_reference, "
                          + "net_amount, tax_amount, tax_rate) "
                          + "VALUES (2, 1, 1, 'other-line-ref', 'merchant-line-ref', 500, "
                          + "105, 0.2100)"))
          .isInstanceOf(SQLException.class);

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO order_item (order_item_id, order_id, "
                          + "product_type_id, order_line_reference, merchant_line_reference, "
                          + "net_amount, tax_amount, tax_rate) "
                          + "VALUES (3, 1, 1, 'line-ref', 'other-merchant-line-ref', 500, "
                          + "105, 0.2100)"))
          .isInstanceOf(SQLException.class);

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_order (order_id, order_reference, "
                          + "merchant_reference, account_id, account_type_id, shopper_id, "
                          + "currency_id, net_amount, tax_amount, gross_amount, "
                          + "idempotency_key, payment_reference, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (2, 'order-ref-2', 'merchant-ref-2', 100, 2, 1, 1, 1000, "
                          + "210, 1210, 'idem-key', 'payment-ref-2', 101, 1, "
                          + "'fingerprint', now())"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void rejectsAnOrderWhoseShopperSubdivisionBelongsToAnotherCountry() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      seedReferenceData(connection);
      execute(connection, "INSERT INTO country VALUES (2, 'US', 'United States')");
      execute(
          connection,
          "INSERT INTO country_subdivision (country_subdivision_id, country_id, code, name) "
              + "VALUES (10, 2, 'US-CA', 'California')");
      execute(
          connection,
          "INSERT INTO shopper_detail (shopper_id, email, full_name, country_id) "
              + "VALUES (1, 'shopper@example.com', 'Shopper', 1)");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_order (order_id, order_reference, "
                          + "merchant_reference, account_id, account_type_id, shopper_id, "
                          + "currency_id, net_amount, tax_amount, gross_amount, "
                          + "idempotency_key, payment_reference, psp_account_id, "
                          + "shopper_country_id, shopper_country_subdivision_id, "
                          + "request_fingerprint, created_ts) "
                          + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, "
                          + "1210, 'idem-key', 'payment-ref', 101, 1, 10, 'fingerprint', now())"))
          .isInstanceOfSatisfying(
              SQLException.class,
              exception -> assertThat(exception.getSQLState()).isEqualTo("23503"));
    }
  }

  @Test
  void rejectsSecondShopperWithSameEmailAndRejectsShopperWithoutEmail() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      seedReferenceData(connection);
      execute(
          connection,
          "INSERT INTO shopper_detail (shopper_id, email, full_name, country_id) "
              + "VALUES (1, 'shopper@example.com', 'Shopper One', 1)");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO shopper_detail (shopper_id, email, full_name, country_id) "
                          + "VALUES (2, 'shopper@example.com', 'Shopper Two', 1)"))
          .isInstanceOf(SQLException.class);
      connection.rollback();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO shopper_detail (shopper_id, full_name, country_id) "
                          + "VALUES (3, 'Shopper Three', 1)"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void orderMerchantAccountTypeResolvesByCodeRegardlessOfSeededIdentifiers() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      execute(connection, "INSERT INTO country VALUES (1, 'NL', 'Netherlands')");
      execute(connection, "INSERT INTO currency VALUES (1, 'EUR', 2)");
      execute(connection, "INSERT INTO account_type VALUES (20, 'MERCHANT'), (40, 'PSP')");
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (100, 20, 'merchant', 'Merchant', true, now()), "
              + "(101, 40, 'psp', 'PSP', true, now())");
      execute(
          connection,
          "INSERT INTO shopper_detail (shopper_id, email, full_name, country_id) "
              + "VALUES (1, 'shopper@example.com', 'Shopper', 1)");
      execute(
          connection,
          "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, "
              + "account_id, account_type_id, shopper_id, currency_id, net_amount, tax_amount, "
              + "gross_amount, idempotency_key, payment_reference, psp_account_id, "
              + "shopper_country_id, request_fingerprint, created_ts) "
              + "VALUES (1, 'order-ref', 'merchant-ref', 100, 20, 1, 1, 1000, 210, 1210, "
              + "'idem-key', 'payment-ref', 101, 1, 'fingerprint', now())");
      connection.commit();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_order (order_id, order_reference, "
                          + "merchant_reference, account_id, account_type_id, shopper_id, "
                          + "currency_id, net_amount, tax_amount, gross_amount, "
                          + "idempotency_key, payment_reference, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (2, 'order-ref-2', 'merchant-ref-2', 101, 40, 1, 1, 1000, "
                          + "210, 1210, 'idem-key-2', 'payment-ref-2', 101, 1, "
                          + "'fingerprint', now())"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  private void seedReferenceData(Connection connection) throws SQLException {
    execute(connection, "INSERT INTO country VALUES (1, 'NL', 'Netherlands')");
    execute(connection, "INSERT INTO currency VALUES (1, 'EUR', 2)");
    execute(connection, "INSERT INTO product_type VALUES (1, 'DIGITAL_GOODS')");
    execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT'), (4, 'PSP')");
    execute(
        connection,
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "VALUES (100, 2, 'merchant', 'Merchant', true, now()), "
            + "(101, 4, 'psp', 'PSP', true, now())");
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
