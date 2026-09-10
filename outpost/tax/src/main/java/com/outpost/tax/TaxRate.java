package com.outpost.tax;

import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable general tax rate for one country or country subdivision. */
public record TaxRate(Country country, @Nullable CountrySubdivision subdivision, BigDecimal rate) {

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
