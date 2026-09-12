package com.outpost.framework.persistence.flyway;

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
              + "gross_amount, idempotency_key, created_ts) "
              + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, 1210, "
              + "'idem-key', now())");
      execute(
          connection,
          "INSERT INTO order_item (order_item_id, order_id, sequence, product_type_id, "
              + "order_line_reference, merchant_line_reference, net_amount, tax_amount, "
              + "tax_rate) VALUES (1, 1, 1, 1, 'line-ref', 'merchant-line-ref', 1000, 210, "
              + "0.2100)");
      execute(
          connection,
          "INSERT INTO order_payment (order_payment_id, order_id, payment_reference, "
              + "psp_account_id, psp_account_type_id, shopper_country_id, "
              + "shopper_country_subdivision_id, created_ts) "
              + "VALUES (1, 1, 'payment-ref', 101, 4, 1, NULL, now())");
      connection.commit();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_order (order_id, order_reference, "
                          + "merchant_reference, account_id, account_type_id, shopper_id, "
                          + "currency_id, net_amount, tax_amount, gross_amount, "
                          + "idempotency_key, created_ts) "
                          + "VALUES (2, 'order-ref-2', 'merchant-ref-2', 101, 4, 1, 1, 1000, "
                          + "210, 1210, 'idem-key-2', now())"))
          .isInstanceOf(SQLException.class);
      connection.rollback();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO order_payment (order_payment_id, order_id, "
                          + "payment_reference, psp_account_id, psp_account_type_id, "
                          + "shopper_country_id, shopper_country_subdivision_id, created_ts) "
                          + "VALUES (2, 1, 'payment-ref-2', 100, 2, 1, NULL, now())"))
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
                          + "idempotency_key, created_ts) "
                          + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, "
                          + "9999, 'idem-key', now())"))
          .isInstanceOf(SQLException.class);

      execute(
          connection,
          "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, "
              + "account_id, account_type_id, shopper_id, currency_id, net_amount, "
              + "tax_amount, gross_amount, idempotency_key, created_ts) "
              + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, 1210, "
              + "'idem-key', now())");
      execute(
          connection,
          "INSERT INTO order_item (order_item_id, order_id, sequence, product_type_id, "
              + "order_line_reference, merchant_line_reference, net_amount, tax_amount, "
              + "tax_rate) VALUES (1, 1, 1, 1, 'line-ref', 'merchant-line-ref', 1000, 210, "
              + "0.2100)");

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO order_item (order_item_id, order_id, sequence, "
                          + "product_type_id, order_line_reference, merchant_line_reference, "
                          + "net_amount, tax_amount, tax_rate) "
                          + "VALUES (2, 1, 2, 1, 'other-line-ref', 'merchant-line-ref', 500, "
                          + "105, 0.2100)"))
          .isInstanceOf(SQLException.class);

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO order_item (order_item_id, order_id, sequence, "
                          + "product_type_id, order_line_reference, merchant_line_reference, "
                          + "net_amount, tax_amount, tax_rate) "
                          + "VALUES (3, 1, 2, 1, 'line-ref', 'other-merchant-line-ref', 500, "
                          + "105, 0.2100)"))
          .isInstanceOf(SQLException.class);

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_order (order_id, order_reference, "
                          + "merchant_reference, account_id, account_type_id, shopper_id, "
                          + "currency_id, net_amount, tax_amount, gross_amount, "
                          + "idempotency_key, created_ts) "
                          + "VALUES (2, 'order-ref-2', 'merchant-ref-2', 100, 2, 1, 1, 1000, "
                          + "210, 1210, 'idem-key', now())"))
          .isInstanceOf(SQLException.class);
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
