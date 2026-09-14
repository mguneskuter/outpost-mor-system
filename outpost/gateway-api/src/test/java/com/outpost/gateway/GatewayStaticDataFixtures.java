package com.outpost.gateway;

import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.ProductTypes;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inserts one database row per constant of every enum {@link
 * com.outpost.platform.staticdata.check.SystemSanityCheck} verifies for Gateway, matching the
 * code-defined id, code, and name exactly.
 */
public final class GatewayStaticDataFixtures {

  private GatewayStaticDataFixtures() {}

  /** Materialises every enum table Gateway's static-data check reads. */
  public static void materializeAll(JdbcTemplate jdbcTemplate) {
    for (Countries country : Countries.values()) {
      var value = country.getValue();
      jdbcTemplate.update(
          "INSERT INTO country (country_id, iso_code, name) VALUES (?, ?, ?)",
          value.getCountryId(),
          value.getIsoCode(),
          value.getName());
    }
    for (CountrySubdivisions subdivision : CountrySubdivisions.values()) {
      var value = subdivision.getValue();
      jdbcTemplate.update(
          "INSERT INTO country_subdivision (country_subdivision_id, country_id, code, name) "
              + "VALUES (?, ?, ?, ?)",
          value.getCountrySubdivisionId(),
          value.getCountry().getCountryId(),
          value.getCode(),
          value.getName());
    }
    for (Currencies currency : Currencies.values()) {
      var value = currency.getValue();
      jdbcTemplate.update(
          "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
          value.getCurrencyId(),
          value.getCurrencyCode(),
          value.getExponent());
    }
    for (ProductTypes productType : ProductTypes.values()) {
      var value = productType.getValue();
      jdbcTemplate.update(
          "INSERT INTO product_type (product_type_id, code) VALUES (?, ?)",
          value.getProductTypeId(),
          value.getCode());
    }
    for (AccountTypes accountType : AccountTypes.values()) {
      var value = accountType.getValue();
      jdbcTemplate.update(
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          value.getAccountTypeId(),
          value.getCode());
    }
    for (FeeModes feeMode : FeeModes.values()) {
      var value = feeMode.getValue();
      jdbcTemplate.update(
          "INSERT INTO fee_mode (fee_mode_id, code) VALUES (?, ?)",
          value.getFeeModeId(),
          value.getCode());
    }
  }
}
