package com.outpost.tax.provider;

import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.tax.TaxRate;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Tax Rate provider to be used at payment checkout and accounting purposes, per country and
 * jurisdiction.
 */
public interface TaxRateProvider {

  /**
   * Resolves the rate for a country and, when applicable, its jurisdiction.
   *
   * @param country the shopper's country
   * @param subdivision the shopper's jurisdiction, or {@code null} for country-level rates
   * @param productType the checkout product type
   * @param asOf the requested day for the rate
   * @return the applicable tax rate
   */
  TaxRate getRate(
      Country country,
      @Nullable CountrySubdivision subdivision,
      ProductType productType,
      LocalDate asOf);
}
