package com.outpost.tax.provider.cached;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.payment.common.ProductTypes;
import com.outpost.tax.TaxRate;
import com.outpost.tax.repository.TaxRateRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CachedTaxRateProviderTest {

  @Test
  void resolvesCountryAndSubdivisionRates() {
    var austria = Countries.AUSTRIA.getValue();
    var california = CountrySubdivisions.US_CA.getValue();
    var provider =
        new CachedTaxRateProvider(
            repository(
                new TaxRate(austria, null, null, new BigDecimal("0.2000")),
                new TaxRate(
                    Countries.UNITED_STATES.getValue(),
                    california,
                    null,
                    new BigDecimal("0.0725"))));

    assertThat(provider.getRate(austria, null, productType()).rate())
        .isEqualByComparingTo("0.2000");
    assertThat(
            provider.getRate(Countries.UNITED_STATES.getValue(), california, productType()).rate())
        .isEqualByComparingTo("0.0725");
  }

  @Test
  void resolvesProductTypeRateAheadOfJurisdictionRate() {
    var unitedStates = Countries.UNITED_STATES.getValue();
    var california = CountrySubdivisions.US_CA.getValue();
    var digitalGoods = ProductTypes.DIGITAL_GOODS.getValue();
    var provider =
        new CachedTaxRateProvider(
            repository(
                new TaxRate(unitedStates, california, null, new BigDecimal("0.0725")),
                new TaxRate(unitedStates, california, digitalGoods, new BigDecimal("0.0000"))));

    assertThat(provider.getRate(unitedStates, california, digitalGoods).rate())
        .isEqualByComparingTo("0.0000");
  }

  @Test
  void resolvesJurisdictionRateForProductTypeWithoutItsOwnRate() {
    var unitedStates = Countries.UNITED_STATES.getValue();
    var california = CountrySubdivisions.US_CA.getValue();
    var provider =
        new CachedTaxRateProvider(
            repository(
                new TaxRate(unitedStates, california, null, new BigDecimal("0.0725")),
                new TaxRate(
                    unitedStates,
                    california,
                    ProductTypes.DIGITAL_GOODS.getValue(),
                    new BigDecimal("0.0000"))));

    assertThat(
            provider
                .getRate(unitedStates, california, ProductTypes.PHYSICAL_GOODS.getValue())
                .rate())
        .isEqualByComparingTo("0.0725");
  }

  @Test
  void rejectsCountryLevelUnitedStatesResolution() {
    var provider =
        new CachedTaxRateProvider(
            repository(
                new TaxRate(
                    Countries.UNITED_STATES.getValue(),
                    CountrySubdivisions.US_CA.getValue(),
                    null,
                    new BigDecimal("0.0725"))));

    assertThatThrownBy(
            () -> provider.getRate(Countries.UNITED_STATES.getValue(), null, productType()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("United States resolution requires a subdivision");
  }

  @Test
  void rejectsDuplicateJurisdictions() {
    var austria = Countries.AUSTRIA.getValue();
    assertThatThrownBy(
            () ->
                new CachedTaxRateProvider(
                    repository(
                        new TaxRate(austria, null, null, new BigDecimal("0.20")),
                        new TaxRate(austria, null, null, new BigDecimal("0.21")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate tax-rate jurisdiction");
  }

  @Test
  void rejectsMissingJurisdictionRates() {
    var provider =
        new CachedTaxRateProvider(
            repository(
                new TaxRate(Countries.AUSTRIA.getValue(), null, null, new BigDecimal("0.20"))));

    assertThatThrownBy(
            () -> provider.getRate(Countries.NETHERLANDS.getValue(), null, productType()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no tax rate for jurisdiction");
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullResolutionArguments() {
    var provider =
        new CachedTaxRateProvider(
            repository(
                new TaxRate(Countries.AUSTRIA.getValue(), null, null, new BigDecimal("0.20"))));

    assertThatThrownBy(() -> provider.getRate(null, null, productType()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> provider.getRate(Countries.AUSTRIA.getValue(), null, null))
        .isInstanceOf(NullPointerException.class);
  }

  private static TaxRateRepository repository(TaxRate... taxRates) {
    return () -> List.of(taxRates);
  }

  private static ProductTypes.ProductType productType() {
    return ProductTypes.DIGITAL_GOODS.getValue();
  }
}
