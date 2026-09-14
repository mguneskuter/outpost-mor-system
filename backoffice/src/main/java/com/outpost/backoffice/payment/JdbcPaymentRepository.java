package com.outpost.backoffice.payment;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads payments from Outpost's order and ledger tables over a read-only connection. */
public final class JdbcPaymentRepository implements PaymentRepository {
  private final JdbcClient jdbc;

  /** Creates a repository over the read-only datasource. */
  public JdbcPaymentRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Payment> findPayments() {
    return jdbc.sql(
            """
            SELECT merchant_order.order_reference,
                   merchant_order.psp_reference,
                   merchant.code AS merchant_code,
                   merchant.name AS merchant_name,
                   psp.name AS psp_name,
                   currency.currency_code,
                   merchant_order.gross_amount,
                   merchant_order.net_amount,
                   merchant_order.tax_amount,
                   country.iso_code AS shopper_country,
                   merchant_order.created_ts,
                   (SELECT string_agg(product_type.code, ',' ORDER BY order_item.order_item_id)
                      FROM order_item
                      JOIN product_type
                        ON product_type.product_type_id = order_item.product_type_id
                     WHERE order_item.order_id = merchant_order.order_id) AS goods_types,
                   (SELECT transaction_event_type.code
                      FROM transaction_event
                      JOIN transaction
                        ON transaction.transaction_id = transaction_event.transaction_id
                      LEFT JOIN transaction parent
                        ON parent.transaction_id = transaction.parent_transaction_id
                      JOIN transaction_event_type
                        ON transaction_event_type.transaction_event_type_id
                           = transaction_event.transaction_event_type_id
                     WHERE merchant_order.order_reference
                           IN (transaction.reference, parent.reference)
                     ORDER BY transaction_event.transaction_event_id DESC
                     LIMIT 1) AS last_event,
                   (SELECT abs(journal_entry_line.quantity)
                      FROM journal_entry
                      JOIN journal_entry_type
                        ON journal_entry_type.journal_entry_type_id
                           = journal_entry.journal_entry_type_id
                       AND journal_entry_type.code = 'FEE_PENDING'
                      JOIN transaction_event
                        ON transaction_event.transaction_event_id
                           = journal_entry.transaction_event_id
                      JOIN transaction
                        ON transaction.transaction_id = transaction_event.transaction_id
                      JOIN journal_entry_line
                        ON journal_entry_line.journal_entry_id = journal_entry.journal_entry_id
                      JOIN register ON register.register_id = journal_entry_line.register_id
                     WHERE transaction.reference = merchant_order.order_reference
                       AND register.account_id = merchant_order.account_id
                     LIMIT 1) AS platform_fee
              FROM merchant_order
              JOIN account merchant ON merchant.account_id = merchant_order.account_id
              JOIN account psp ON psp.account_id = merchant_order.psp_account_id
              JOIN currency ON currency.currency_id = merchant_order.currency_id
              JOIN country ON country.country_id = merchant_order.shopper_country_id
             ORDER BY merchant_order.created_ts DESC, merchant_order.order_id DESC
            """)
        .query(
            (row, index) ->
                new Payment(
                    row.getString("order_reference"),
                    row.getString("psp_reference"),
                    row.getString("merchant_code"),
                    row.getString("merchant_name"),
                    row.getString("psp_name"),
                    row.getString("last_event"),
                    row.getString("currency_code"),
                    row.getLong("gross_amount"),
                    row.getLong("net_amount"),
                    row.getLong("tax_amount"),
                    row.getObject("platform_fee", Long.class),
                    row.getString("shopper_country"),
                    goodsTypes(row.getString("goods_types")),
                    row.getObject("created_ts", OffsetDateTime.class).toInstant()))
        .list();
  }

  @Override
  public List<PaymentEvent> findPaymentEvents(String orderReference) {
    return jdbc.sql(
            """
            SELECT transaction_event.transaction_event_id,
                   transaction_type.code AS transaction_type,
                   transaction.reference,
                   currency.currency_code,
                   transaction.quantity,
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
              JOIN currency ON currency.currency_id = transaction.currency_id
             WHERE :orderReference IN (transaction.reference, parent.reference)
             ORDER BY transaction_event.transaction_event_id
            """)
        .param("orderReference", orderReference)
        .query(
            (row, index) ->
                new PaymentEvent(
                    row.getLong("transaction_event_id"),
                    row.getString("transaction_type"),
                    row.getString("reference"),
                    row.getString("currency_code"),
                    row.getLong("quantity"),
                    row.getString("event_type"),
                    row.getObject("event_ts", OffsetDateTime.class).toInstant()))
        .list();
  }

  @Override
  public List<PaymentJournalLine> findPaymentJournalLines(String orderReference) {
    return jdbc.sql(
            """
            SELECT journal_entry.journal_entry_id,
                   journal_entry_type.code AS entry_type,
                   transaction_event_type.code AS event_type,
                   transaction.reference,
                   journal_entry.posted,
                   account.code AS account_code,
                   account.name AS account_name,
                   register_type.register_type_code,
                   currency.currency_code,
                   journal_entry_line.quantity
              FROM journal_entry
              JOIN journal_entry_type
                ON journal_entry_type.journal_entry_type_id = journal_entry.journal_entry_type_id
              JOIN transaction_event
                ON transaction_event.transaction_event_id = journal_entry.transaction_event_id
              JOIN transaction_event_type
                ON transaction_event_type.transaction_event_type_id
                   = transaction_event.transaction_event_type_id
              JOIN transaction ON transaction.transaction_id = transaction_event.transaction_id
              LEFT JOIN transaction parent
                ON parent.transaction_id = transaction.parent_transaction_id
              JOIN journal_entry_line
                ON journal_entry_line.journal_entry_id = journal_entry.journal_entry_id
              JOIN register ON register.register_id = journal_entry_line.register_id
              JOIN account ON account.account_id = register.account_id
              JOIN register_type ON register_type.register_type_id = register.register_type_id
              JOIN currency ON currency.currency_id = journal_entry_line.currency_id
             WHERE :orderReference IN (transaction.reference, parent.reference)
             ORDER BY journal_entry.journal_entry_id, journal_entry_line.journal_entry_line_id
            """)
        .param("orderReference", orderReference)
        .query(
            (row, index) ->
                new PaymentJournalLine(
                    row.getLong("journal_entry_id"),
                    row.getString("entry_type"),
                    row.getString("event_type"),
                    row.getString("reference"),
                    row.getObject("posted", OffsetDateTime.class).toInstant(),
                    row.getString("account_code"),
                    row.getString("account_name"),
                    row.getString("register_type_code"),
                    row.getString("currency_code"),
                    row.getLong("quantity")))
        .list();
  }

  private static List<String> goodsTypes(@Nullable String joined) {
    return joined == null ? List.of() : Arrays.asList(joined.split(","));
  }
}
