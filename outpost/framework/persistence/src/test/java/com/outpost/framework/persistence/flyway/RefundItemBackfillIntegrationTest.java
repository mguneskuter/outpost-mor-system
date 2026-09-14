package com.outpost.framework.persistence.flyway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Refunds stored before refund items existed covered their whole order: the migration gives the
 * earliest refund of each order every line, and any later refund of the same order the same lines
 * as failed, so every stored refund stays readable and no line is claimed twice.
 */
class RefundItemBackfillIntegrationTest {
  private static final String BEFORE_REFUND_ITEMS = "2026091306";

  private PostgreSQLContainer<?> database;

  @BeforeEach
  void startDatabase() {
    database =
        PostgresTestDatabase.startContainer(
            "outpost_refund_backfill", "outpost_refund_backfill", "outpost_refund_backfill");
    database.start();
  }

  @AfterEach
  void stopDatabase() {
    database.stop();
  }

  @Test
  void claimsEveryLineForTheEarliestRefundAndHoldsLaterRefundsOfTheOrderAsFailed()
      throws SQLException {
    migrateTo(BEFORE_REFUND_ITEMS);
    try (Connection connection = database.createConnection("")) {
      seedOrderWithTwoLines(connection);
      execute(
          connection,
          "INSERT INTO merchant_refund (refund_id, refund_reference, order_id, "
              + "original_reference, merchant_reference, idempotency_key, psp_refund_reference, "
              + "created_ts) VALUES "
              + "(1, 'refund-first', 1, 'order-ref', 'merchant-refund-1', 'key-1', '77', now()), "
              + "(2, 'refund-again', 1, 'order-ref', 'merchant-refund-2', 'key-2', '78', now())");
    }

    migrateToLatest();

    try (Connection connection = database.createConnection("")) {
      assertThat(
              query(
                  connection,
                  "SELECT refund_id || ':' || order_item_id || ':' || refund_failed "
                      + "FROM refund_item ORDER BY refund_id, order_item_id"))
          .containsExactly("1:1:false", "1:2:false", "2:1:true", "2:2:true");
    }
  }

  private void migrateTo(String target) {
    flyway().target(target).load().migrate();
  }

  private void migrateToLatest() {
    flyway().load().migrate();
  }

  private FluentConfiguration flyway() {
    return Flyway.configure()
        .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .schemas("public")
        .defaultSchema("public");
  }

  private static void seedOrderWithTwoLines(Connection connection) throws SQLException {
    execute(connection, "INSERT INTO country VALUES (1, 'NL', 'Netherlands')");
    execute(connection, "INSERT INTO currency VALUES (1, 'EUR', 2)");
    execute(connection, "INSERT INTO product_type VALUES (1, 'DIGITAL_GOODS')");
    execute(connection, "INSERT INTO account_type VALUES (2, 'MERCHANT'), (4, 'PSP')");
    execute(
        connection,
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "VALUES (100, 2, 'merchant', 'Merchant', true, now()), "
            + "(101, 4, 'psp', 'PSP', true, now())");
    execute(
        connection,
        "INSERT INTO shopper_detail (shopper_id, email, full_name, country_id) "
            + "VALUES (1, 'shopper@example.com', 'Shopper', 1)");
    execute(
        connection,
        "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, "
            + "account_id, account_type_id, shopper_id, currency_id, net_amount, tax_amount, "
            + "gross_amount, idempotency_key, psp_account_id, shopper_country_id, "
            + "request_fingerprint, psp_reference, created_ts) "
            + "VALUES (1, 'order-ref', 'merchant-ref', 100, 2, 1, 1, 1000, 210, 1210, "
            + "'idem-key', 101, 1, 'fingerprint', 'psp-1', now())");
    execute(
        connection,
        "INSERT INTO order_item (order_item_id, order_id, product_type_id, "
            + "order_line_reference, merchant_line_reference, net_amount, tax_amount, tax_rate) "
            + "VALUES (1, 1, 1, 'line-1', 'merchant-line-1', 600, 126, 0.2100), "
            + "(2, 1, 1, 'line-2', 'merchant-line-2', 400, 84, 0.2100)");
  }

  private static List<String> query(Connection connection, String sql) throws SQLException {
    List<String> rows = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      while (result.next()) {
        rows.add(result.getString(1));
      }
    }
    return rows;
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
