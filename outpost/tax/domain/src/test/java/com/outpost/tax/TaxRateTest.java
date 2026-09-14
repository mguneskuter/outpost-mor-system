package com.outpost.tax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TaxRateTest {
  @Test
  void acceptsCountryRateAndSubdivisionRate() {
    TaxRate countryRate =
        new TaxRate(Countries.AUSTRIA.getValue(), null, null, new BigDecimal("0.2000"));
    TaxRate subdivisionRate =
        new TaxRate(
            Countries.UNITED_STATES.getValue(),
            CountrySubdivisions.US_CA.getValue(),
            null,
            new BigDecimal("0.0725"));

    assertEquals(Countries.AUSTRIA.getValue(), countryRate.country());
    assertEquals(CountrySubdivisions.US_CA.getValue(), subdivisionRate.subdivision());
  }

  @Test
  void rejectsNegativeRates() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TaxRate(Countries.AUSTRIA.getValue(), null, null, new BigDecimal("-0.01")));
  }

  @Test
  void rejectsSubdivisionFromAnotherCountry() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TaxRate(
                Countries.AUSTRIA.getValue(),
                CountrySubdivisions.US_CA.getValue(),
                null,
                new BigDecimal("0.20")));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullRequiredValues() {
    assertThrows(
        NullPointerException.class, () -> new TaxRate(null, null, null, new BigDecimal("0.20")));
    assertThrows(
        NullPointerException.class,
        () -> new TaxRate(Countries.AUSTRIA.getValue(), null, null, null));
  }
}
