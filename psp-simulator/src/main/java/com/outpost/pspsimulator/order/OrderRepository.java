package com.outpost.pspsimulator.order;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Persists the simulator's orders in the {@code psp_simulator} schema. */
@Repository
public class OrderRepository {

  private static final RowMapper<Order> ROW_MAPPER =
      (resultSet, rowNum) ->
          new Order(
              resultSet.getString("psp_code"),
              resultSet.getLong("psp_reference"),
              resultSet.getString("payment_reference"),
              resultSet.getLong("amount"),
              resultSet.getString("currency_code"),
              OrderStatuses.fromCode(resultSet.getString("status")));

  private final JdbcTemplate jdbcTemplate;

  /** Creates a repository backed by the simulator database. */
  public OrderRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Inserts a new order for the payment reference, or does nothing when that reference already
   * exists for the PSP.
   *
   * @return the inserted order, or empty when the payment reference is already in use
   */
  public Optional<Order> insert(
      String pspCode, String paymentReference, long amountMinor, String currencyCode) {
    List<Order> rows =
        jdbcTemplate.query(
            """
            INSERT INTO psp_order (psp_code, payment_reference, amount, currency_code, status)
            VALUES (?, ?, ?, ?, 'CREATED')
            ON CONFLICT (psp_code, payment_reference) DO NOTHING
            RETURNING psp_reference, psp_code, payment_reference, amount, currency_code, status
            """,
            ROW_MAPPER,
            pspCode,
            paymentReference,
            amountMinor,
            currencyCode);
    return rows.stream().findFirst();
  }

  /** Returns the order with the supplied PSP reference, scoped to the PSP. */
  public Optional<Order> findByPspReference(String pspCode, long pspReference) {
    List<Order> rows =
        jdbcTemplate.query(
            """
            SELECT psp_reference, psp_code, payment_reference, amount, currency_code, status
            FROM psp_order
            WHERE psp_code = ? AND psp_reference = ?
            """,
            ROW_MAPPER,
            pspCode,
            pspReference);
    return rows.stream().findFirst();
  }

  /** Returns the order with the supplied payment reference, scoped to the PSP. */
  public Optional<Order> findByPaymentReference(String pspCode, String paymentReference) {
    List<Order> rows =
        jdbcTemplate.query(
            """
            SELECT psp_reference, psp_code, payment_reference, amount, currency_code, status
            FROM psp_order
            WHERE psp_code = ? AND payment_reference = ?
            """,
            ROW_MAPPER,
            pspCode,
            paymentReference);
    return rows.stream().findFirst();
  }

  /**
   * Moves an order from one status to the next, only when it is still in the expected status.
   *
   * @return whether the transition happened; {@code false} when the order moved on in the meantime
   */
  public boolean transition(
      String pspCode, long pspReference, OrderStatuses from, OrderStatuses to) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE psp_order
            SET status = ?
            WHERE psp_code = ? AND psp_reference = ? AND status = ?
            """,
            to.getCode(),
            pspCode,
            pspReference,
            from.getCode());
    return updated == 1;
  }
}
