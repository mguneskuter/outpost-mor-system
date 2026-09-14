package com.outpost.backoffice.merchant;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads merchants, PSPs, and countries straight from Outpost's tables over a read-only connection.
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
  public List<String> findCountryCodes() {
    return jdbc.sql("SELECT iso_code FROM country ORDER BY iso_code").query(String.class).list();
  }
}
