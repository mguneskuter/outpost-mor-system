package com.outpost.tax.provider.cached;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.payment.common.ProductTypes;
import com.outpost.tax.TaxRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CachedTaxRateProviderTest {

  @Test
  void resolvesCountryAndSubdivisionRates() {
    var austria = Countries.AUSTRIA.value();
    var california = CountrySubdivisions.US_CA.value();
    var provider =
        new CachedTaxRateProvider(
            List.of(
                new TaxRate(austria, null, new BigDecimal("0.2000")),
                new TaxRate(
                    Countries.UNITED_STATES.value(), california, new BigDecimal("0.0725"))));

    assertThat(provider.resolve(austria, null, productType(), date()).rate())
        .isEqualByComparingTo("0.2000");
    assertThat(
            provider
                .resolve(Countries.UNITED_STATES.value(), california, productType(), date())
                .rate())
        .isEqualByComparingTo("0.0725");
  }

  @Test
  void rejectsCountryLevelUnitedStatesResolution() {
    var provider =
        new CachedTaxRateProvider(
            List.of(
                new TaxRate(
                    Countries.UNITED_STATES.value(),
                    CountrySubdivisions.US_CA.value(),
                    new BigDecimal("0.0725"))));

    assertThatThrownBy(
            () -> provider.resolve(Countries.UNITED_STATES.value(), null, productType(), date()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("United States resolution requires a subdivision");
  }

  private static ProductTypes.ProductType productType() {
    return ProductTypes.DIGITAL_GOODS.value();
  }

  private static LocalDate date() {
    return LocalDate.of(2026, 9, 10);
  }
}
