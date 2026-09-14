package com.outpost.tax.provider;

import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.tax.TaxRate;
import org.jspecify.annotations.Nullable;

/** Resolves the tax rate an order line is charged at checkout. */
public interface TaxRateProvider {

  /**
   * Resolves the rate that applies now to a product type sold in a jurisdiction. The jurisdiction's
   * rate for that product type, when it has one, takes precedence over its rate for every product
   * type.
   *
   * <p>No rate is looked up for an earlier date. Tax rates change on the order of once a year, so
   * one current rate set is held rather than a dated history, and a past sale keeps the rate
   * persisted with it.
   *
   * @param country the shopper's country
   * @param subdivision the shopper's country subdivision, or {@code null} for a country-level rate
   * @param productType the product type of the order line
   * @return the applicable tax rate
   * @throws IllegalArgumentException if no rate applies to the jurisdiction and product type
   */
  TaxRate getRate(
      Country country, @Nullable CountrySubdivision subdivision, ProductType productType);
}
