package com.outpost.pspsimulator.refund;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Persists the simulator's refunds in the {@code psp_simulator} schema. */
@Repository
public class RefundRepository {

  private static final RowMapper<Refund> ROW_MAPPER =
      (resultSet, rowNum) ->
          new Refund(
              resultSet.getString("psp_code"),
              resultSet.getString("psp_reference"),
              resultSet.getString("psp_refund_reference"),
              resultSet.getString("refund_reference"),
              resultSet.getLong("amount"),
              resultSet.getString("currency_code"),
              resultSet.getBoolean("succeeded"));

  private final JdbcTemplate jdbcTemplate;

  /** Creates a repository backed by the simulator database. */
  public RefundRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Inserts a new refund under a fresh PSP refund reference, or does nothing when the refund
   * reference already exists for the PSP.
   *
   * @return the inserted refund, or empty when the refund reference is already in use
   */
  public Optional<Refund> insert(
      String pspCode,
      String pspReference,
      String refundReference,
      long amountMinor,
      String currencyCode,
      boolean succeeded) {
    List<Refund> rows =
        jdbcTemplate.query(
            """
            INSERT INTO psp_refund (
                psp_refund_reference, psp_code, psp_reference, refund_reference, amount,
                currency_code, succeeded
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (psp_code, refund_reference) DO NOTHING
            RETURNING
                psp_refund_reference,
                psp_code,
                psp_reference,
                refund_reference,
                amount,
                currency_code,
                succeeded
            """,
            ROW_MAPPER,
            "psp-refund-" + UUID.randomUUID(),
            pspCode,
            pspReference,
            refundReference,
            amountMinor,
            currencyCode,
            succeeded);
    return rows.stream().findFirst();
  }

  /** Returns the refund with the supplied refund reference, scoped to the PSP. */
  public Optional<Refund> findByRefundReference(String pspCode, String refundReference) {
    List<Refund> rows =
        jdbcTemplate.query(
            """
            SELECT
                psp_refund_reference,
                psp_code,
                psp_reference,
                refund_reference,
                amount,
                currency_code,
                succeeded
            FROM psp_refund
            WHERE psp_code = ? AND refund_reference = ?
            """,
            ROW_MAPPER,
            pspCode,
            refundReference);
    return rows.stream().findFirst();
  }

  /** Returns the sum in minor units of the order's refunds that succeeded. */
  public long sumSucceededAmount(String pspCode, String pspReference) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(amount), 0)
            FROM psp_refund
            WHERE psp_code = ? AND psp_reference = ? AND succeeded
            """,
            Long.class,
            pspCode,
            pspReference));
  }
}
