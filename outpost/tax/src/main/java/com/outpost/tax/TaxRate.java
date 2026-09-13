package com.outpost.tax;

import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.ProductTypes.ProductType;
import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable tax rate for one country or country subdivision. A rate with a product type applies to
 * that product type only; a rate without one applies to every product type that has no rate of its
 * own in the same jurisdiction.
 */
public record TaxRate(
    Country country,
    @Nullable CountrySubdivision subdivision,
    @Nullable ProductType productType,
    BigDecimal rate) {

  /** Validates the jurisdiction and non-negative fractional rate. */
  public TaxRate {
    Objects.requireNonNull(country, "country");
    Objects.requireNonNull(rate, "rate");
    if (rate.signum() < 0) {
      throw new IllegalArgumentException("rate must not be negative: " + rate);
    }
    if (subdivision != null && !subdivision.getCountry().equals(country)) {
      throw new IllegalArgumentException("subdivision must belong to country");
    }
  }
}
