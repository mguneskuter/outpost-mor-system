package com.outpost.backoffice.register;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Sums journal lines per register and currency over a read-only connection. */
public final class JdbcRegisterBalanceRepository implements RegisterBalanceRepository {
  private static final String HELD_ACCOUNT_TYPES =
      "('MERCHANT', 'TAX_AUTHORITY', 'PSP', 'PLATFORM')";

  private final JdbcClient jdbc;

  /** Creates a repository over the read-only datasource. */
  public JdbcRegisterBalanceRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<RegisterBalance> findBalances(
      List<String> accountTypes, List<String> accountCodes, List<String> registerTypes) {
    // The operating currencies are the ones a merchant is charged a fee in; a register is listed
    // in each of them.
    return jdbc.sql(
            """
            SELECT account_type.code AS account_type,
                   account.code AS account_code,
                   account.name AS account_name,
                   register_type.register_type_code,
                   operating.currency_code,
                   COALESCE(SUM(GREATEST(journal_entry_line.quantity, 0)), 0) AS debits,
                   COALESCE(SUM(GREATEST(-journal_entry_line.quantity, 0)), 0) AS credits
              FROM account
              JOIN account_type ON account_type.account_type_id = account.account_type_id
              JOIN register ON register.account_id = account.account_id
              JOIN register_type ON register_type.register_type_id = register.register_type_id
             CROSS JOIN (SELECT DISTINCT currency.currency_id, currency.currency_code
                           FROM currency
                           JOIN merchant_fee_configuration
                             ON merchant_fee_configuration.currency_id = currency.currency_id)
                        operating
              LEFT JOIN journal_entry_line
                ON journal_entry_line.register_id = register.register_id
               AND journal_entry_line.currency_id = operating.currency_id
             WHERE account_type.code IN """
                + HELD_ACCOUNT_TYPES
                + """

               AND (:anyAccountType OR account_type.code IN (:accountTypes))
               AND (:anyAccountCode OR account.code IN (:accountCodes))
               AND (:anyRegisterType OR register_type.register_type_code IN (:registerTypes))
             GROUP BY account_type.code, account.code, account.name,
                      register_type.register_type_code, operating.currency_code
             ORDER BY account_type.code, account.code, register_type.register_type_code,
                      operating.currency_code
            """)
        .param("anyAccountType", accountTypes.isEmpty())
        .param("accountTypes", orPlaceholder(accountTypes))
        .param("anyAccountCode", accountCodes.isEmpty())
        .param("accountCodes", orPlaceholder(accountCodes))
        .param("anyRegisterType", registerTypes.isEmpty())
        .param("registerTypes", orPlaceholder(registerTypes))
        .query(
            (row, index) ->
                new RegisterBalance(
                    row.getString("account_type"),
                    row.getString("account_code"),
                    row.getString("account_name"),
                    row.getString("register_type_code"),
                    row.getString("currency_code"),
                    row.getLong("debits"),
                    row.getLong("credits")))
        .list();
  }

  @Override
  public List<String> findAccountTypeCodes() {
    return jdbc.sql(
            """
            SELECT DISTINCT account_type.code
              FROM account_type
              JOIN account ON account.account_type_id = account_type.account_type_id
              JOIN register ON register.account_id = account.account_id
             WHERE account_type.code IN """
                + HELD_ACCOUNT_TYPES
                + " ORDER BY account_type.code")
        .query(String.class)
        .list();
  }

  @Override
  public List<String> findAccountCodes(List<String> accountTypes) {
    return jdbc.sql(
            """
            SELECT DISTINCT account.code
              FROM account
              JOIN account_type ON account_type.account_type_id = account.account_type_id
              JOIN register ON register.account_id = account.account_id
             WHERE account_type.code IN """
                + HELD_ACCOUNT_TYPES
                + """

               AND (:anyAccountType OR account_type.code IN (:accountTypes))
             ORDER BY account.code
            """)
        .param("anyAccountType", accountTypes.isEmpty())
        .param("accountTypes", orPlaceholder(accountTypes))
        .query(String.class)
        .list();
  }

  @Override
  public List<String> findRegisterTypeCodes() {
    return jdbc.sql("SELECT register_type_code FROM register_type ORDER BY register_type_code")
        .query(String.class)
        .list();
  }

  /** An IN list needs at least one value; a filter left empty is disabled by its flag instead. */
  private static List<String> orPlaceholder(List<String> values) {
    return values.isEmpty() ? List.of("") : values;
  }
}
