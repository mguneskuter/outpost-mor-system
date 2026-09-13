package com.outpost.merchant.cli.merchant;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads merchants and order payments straight from Outpost's tables over a read-only connection.
 */
public final class JdbcMerchantRepository implements MerchantRepository {
  private final JdbcClient jdbc;

  /** Creates a repository over the read-only datasource. */
  public JdbcMerchantRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Merchant> findActiveMerchants() {
    return jdbc.sql(
            """
            SELECT account.code, account.name
              FROM account
              JOIN account_type ON account_type.account_type_id = account.account_type_id
             WHERE account_type.code = 'MERCHANT' AND account.is_active
             ORDER BY account.code
            """)
        .query((row, index) -> new Merchant(row.getString("code"), row.getString("name")))
        .list();
  }

  @Override
  public List<Psp> findEnabledPsps(String merchantCode) {
    return jdbc.sql(
            """
            SELECT psp.code, psp.name
              FROM merchant_psp
              JOIN account merchant ON merchant.account_id = merchant_psp.account_id
              JOIN account psp ON psp.account_id = merchant_psp.psp_account_id
             WHERE merchant.code = :merchantCode AND psp.is_active
             ORDER BY psp.code
            """)
        .param("merchantCode", merchantCode)
        .query((row, index) -> new Psp(row.getString("code"), row.getString("name")))
        .list();
  }

  @Override
  public Optional<OrderPayment> findOrderPayment(String orderReference) {
    return jdbc.sql(
            """
            SELECT psp_reference, payment_link
              FROM merchant_order
             WHERE order_reference = :orderReference
               AND psp_reference IS NOT NULL AND payment_link IS NOT NULL
            """)
        .param("orderReference", orderReference)
        .query(
            (row, index) ->
                new OrderPayment(row.getString("psp_reference"), row.getString("payment_link")))
        .optional();
  }

  @Override
  public List<OrderEvent> findOrderEvents(String orderReference) {
    return jdbc.sql(
            """
            SELECT transaction_type.code AS transaction_type,
                   transaction_event_type.code AS event_type,
                   transaction_event.event_ts
              FROM transaction_event
              JOIN transaction ON transaction.transaction_id = transaction_event.transaction_id
              LEFT JOIN transaction parent
                ON parent.transaction_id = transaction.parent_transaction_id
              JOIN transaction_type
                ON transaction_type.transaction_type_id = transaction.transaction_type_id
              JOIN transaction_event_type
                ON transaction_event_type.transaction_event_type_id
                   = transaction_event.transaction_event_type_id
             WHERE :orderReference IN (transaction.reference, parent.reference)
             ORDER BY transaction_event.transaction_event_id
            """)
        .param("orderReference", orderReference)
        .query(
            (row, index) ->
                new OrderEvent(
                    row.getString("transaction_type"),
                    row.getString("event_type"),
                    row.getObject("event_ts", OffsetDateTime.class).toInstant()))
        .list();
  }
}
