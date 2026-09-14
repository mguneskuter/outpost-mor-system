package com.outpost.framework.persistence.flyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;

class PaymentSchemaIntegrationTest {

  private static final String RAISED_EXCEPTION = "P0001";

  private static final String STORE_PSP_FACTS =
      "UPDATE merchant_order SET psp_reference = 'psp-ref', "
          + "payment_link = 'https://psp.example/pay' WHERE order_id = 1";

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
              + "gross_amount, idempotency_key, psp_account_id, "
              + "shopper_country_id, request_fingerprint, created_ts) "
              + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, 1210, "
              + "'idem-key', 101, 1, 'fingerprint', now())");
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
                          + "idempotency_key, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (2, 'order-ref-2', 'merchant-ref-2', 101, 4, 1, 1, 1000, "
                          + "210, 1210, 'idem-key-2', 101, 1, "
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
                          + "idempotency_key, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (3, 'order-ref-3', 'merchant-ref-3', 100, 2, 1, 1, 1000, "
                          + "210, 1210, 'idem-key-3', 100, 1, "
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
                          + "idempotency_key, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, "
                          + "9999, 'idem-key', 101, 1, 'fingerprint', now())"))
          .isInstanceOf(SQLException.class);

      execute(
          connection,
          "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, "
              + "account_id, account_type_id, shopper_id, currency_id, net_amount, "
              + "tax_amount, gross_amount, idempotency_key, psp_account_id, "
              + "shopper_country_id, request_fingerprint, created_ts) "
              + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, 1210, "
              + "'idem-key', 101, 1, 'fingerprint', now())");
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
                          + "idempotency_key, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (2, 'order-ref-2', 'merchant-ref-2', 100, 2, 1, 1, 1000, "
                          + "210, 1210, 'idem-key', 101, 1, "
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
                          + "idempotency_key, psp_account_id, "
                          + "shopper_country_id, shopper_country_subdivision_id, "
                          + "request_fingerprint, created_ts) "
                          + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, "
                          + "1210, 'idem-key', 101, 1, 10, 'fingerprint', now())"))
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
              + "gross_amount, idempotency_key, psp_account_id, "
              + "shopper_country_id, request_fingerprint, created_ts) "
              + "VALUES (1, 'order-ref', 'merchant-ref', 100, 20, 1, 1, 1000, 210, 1210, "
              + "'idem-key', 101, 1, 'fingerprint', now())");
      connection.commit();

      assertThatThrownBy(
              () ->
                  execute(
                      connection,
                      "INSERT INTO merchant_order (order_id, order_reference, "
                          + "merchant_reference, account_id, account_type_id, shopper_id, "
                          + "currency_id, net_amount, tax_amount, gross_amount, "
                          + "idempotency_key, psp_account_id, "
                          + "shopper_country_id, request_fingerprint, created_ts) "
                          + "VALUES (2, 'order-ref-2', 'merchant-ref-2', 101, 40, 1, 1, 1000, "
                          + "210, 1210, 'idem-key-2', 101, 1, "
                          + "'fingerprint', now())"))
          .isInstanceOf(SQLException.class);
      connection.rollback();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("storedOrderFactChanges")
  void rejectsChangingStoredOrderFact(String fact, String assignment) throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      insertOrder(connection);

      assertRejectedAsImmutable(
          connection, "UPDATE merchant_order SET " + assignment + " WHERE order_id = 1");
      connection.rollback();
    }
  }

  static Stream<Arguments> storedOrderFactChanges() {
    return Stream.of(
        Arguments.of("identity", "order_reference = 'other-order-ref'"),
        Arguments.of("merchant reference", "merchant_reference = 'other-merchant-ref'"),
        Arguments.of("ownership", "account_id = 102"),
        Arguments.of("shopper link", "shopper_id = 2"),
        Arguments.of("shopper country", "shopper_country_id = 3"),
        Arguments.of("shopper subdivision", "shopper_country_subdivision_id = 10"),
        Arguments.of("currency", "currency_id = 2"),
        Arguments.of("amounts", "net_amount = 900, gross_amount = 1110"),
        Arguments.of("idempotency key", "idempotency_key = 'other-idem-key'"),
        Arguments.of("request fingerprint", "request_fingerprint = 'other-fingerprint'"),
        Arguments.of("PSP account", "psp_account_id = 103"),
        Arguments.of("creation time", "created_ts = created_ts - interval '1 day'"));
  }

  @Test
  void rejectsDeletingAnOrder() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      insertOrder(connection);

      assertRejectedAsImmutable(connection, "DELETE FROM merchant_order WHERE order_id = 1");
      connection.rollback();
    }
  }

  @Test
  void storesPspReferenceAndPaymentLinkOnceAndAcceptsAnIdenticalRewrite() throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      insertOrder(connection);

      execute(connection, STORE_PSP_FACTS);
      execute(connection, STORE_PSP_FACTS);

      try (Statement statement = connection.createStatement();
          ResultSet row =
              statement.executeQuery(
                  "SELECT psp_reference, payment_link FROM merchant_order WHERE order_id = 1")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getString("psp_reference")).isEqualTo("psp-ref");
        assertThat(row.getString("payment_link")).isEqualTo("https://psp.example/pay");
      }
      connection.rollback();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "psp_reference = 'other-psp-ref'",
        "payment_link = 'https://psp.example/other'",
        "psp_reference = NULL"
      })
  void rejectsReplacingStoredPspFact(String assignment) throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      insertOrder(connection);
      execute(connection, STORE_PSP_FACTS);

      assertRejectedAsImmutable(
          connection, "UPDATE merchant_order SET " + assignment + " WHERE order_id = 1");
      connection.rollback();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "UPDATE order_item SET tax_rate = 0.0900 WHERE order_item_id = 1",
        "DELETE FROM order_item WHERE order_item_id = 2"
      })
  void rejectsChangingStoredOrderLine(String statement) throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      insertOrder(connection);
      insertOrderItem(connection, 1, 1, 600, 126);
      insertOrderItem(connection, 2, 1, 400, 84);

      assertRejectedAsImmutable(connection, statement);
      connection.rollback();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "UPDATE merchant_refund SET merchant_reference = 'other' "
            + "WHERE refund_reference = 'refund-1'",
        "UPDATE merchant_refund SET psp_refund_reference = 'other' "
            + "WHERE refund_reference = 'refund-1'",
        "DELETE FROM merchant_refund WHERE refund_reference = 'refund-1'"
      })
  void merchantRefundIsImmutableOnceItsPspRefundReferenceIsStored(String statement)
      throws SQLException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      insertOrder(connection);
      execute(
          connection,
          "INSERT INTO merchant_refund (refund_reference, order_id, original_reference, "
              + "merchant_reference, idempotency_key, psp_refund_reference, created_ts) "
              + "VALUES ('refund-1', 1, 'order-ref', 'merchant-refund-1', 'refund-key', '77', "
              + "now())");

      assertRejectedAsImmutable(connection, statement);
      connection.rollback();
    }
  }

  @Test
  void orderLineSumQueryReportsOnlyOrdersWhoseTotalsDifferFromTheirLines()
      throws SQLException, IOException {
    try (Connection connection = database.createConnection("")) {
      connection.setAutoCommit(false);
      insertOrder(connection);
      insertOrderItem(connection, 1, 1, 600, 126);
      insertOrderItem(connection, 2, 1, 400, 84);
      execute(
          connection,
          "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, "
              + "account_id, account_type_id, shopper_id, currency_id, net_amount, tax_amount, "
              + "gross_amount, idempotency_key, psp_account_id, "
              + "shopper_country_id, request_fingerprint, created_ts) "
              + "VALUES (2, 'order-ref-2', 'merchant-ref-2', 100, 2, 1, 1, 1000, 210, 1210, "
              + "'idem-key-2', 101, 1, 'fingerprint', now())");
      insertOrderItem(connection, 3, 2, 900, 210);

      assertThat(
              reportedReferences(
                  connection, "merchant_order_line_sum_mismatch.sql", "order_reference"))
          .containsExactly("order-ref-2");
      connection.rollback();
    }
  }

  private void insertOrder(Connection connection) throws SQLException {
    seedReferenceData(connection);
    execute(connection, "INSERT INTO country VALUES (3, 'BE', 'Belgium')");
    execute(
        connection,
        "INSERT INTO country_subdivision (country_subdivision_id, country_id, code, name) "
            + "VALUES (10, 1, 'NL-NH', 'Noord-Holland')");
    execute(connection, "INSERT INTO currency VALUES (2, 'USD', 2)");
    execute(
        connection,
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "VALUES (102, 2, 'other-merchant', 'Other Merchant', true, now()), "
            + "(103, 4, 'other-psp', 'Other PSP', true, now())");
    execute(
        connection,
        "INSERT INTO shopper_detail (shopper_id, email, full_name, country_id) "
            + "VALUES (1, 'shopper@example.com', 'Shopper', 1), "
            + "(2, 'other@example.com', 'Other Shopper', 1)");
    execute(
        connection,
        "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, "
            + "account_id, account_type_id, shopper_id, currency_id, net_amount, tax_amount, "
            + "gross_amount, idempotency_key, psp_account_id, "
            + "shopper_country_id, request_fingerprint, created_ts) "
            + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, 1210, "
            + "'idem-key', 101, 1, 'fingerprint', now())");
  }

  private static void insertOrderItem(
      Connection connection, long orderItemId, long orderId, long netAmount, long taxAmount)
      throws SQLException {
    execute(
        connection,
        "INSERT INTO order_item (order_item_id, order_id, product_type_id, "
            + "order_line_reference, merchant_line_reference, net_amount, tax_amount, tax_rate) "
            + "VALUES ("
            + orderItemId
            + ", "
            + orderId
            + ", 1, 'line-ref-"
            + orderItemId
            + "', 'merchant-line-ref-"
            + orderItemId
            + "', "
            + netAmount
            + ", "
            + taxAmount
            + ", 0.2100)");
  }

  private static void assertRejectedAsImmutable(Connection connection, String sql) {
    assertThatThrownBy(() -> execute(connection, sql))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> assertThat(exception.getSQLState()).isEqualTo(RAISED_EXCEPTION));
  }

  private static List<String> reportedReferences(
      Connection connection, String controlFileName, String referenceColumn)
      throws SQLException, IOException {
    String query =
        Files.readString(
            Path.of(
                Objects.requireNonNull(System.getProperty("outpost.control.location")),
                controlFileName));
    List<String> references = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(query)) {
      while (rows.next()) {
        references.add(rows.getString(referenceColumn));
      }
    }
    return references;
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
