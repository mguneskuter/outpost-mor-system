package com.outpost.tax.provider.cached;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.tax.TaxRate;
import com.outpost.tax.provider.TaxRateProvider;
import com.outpost.tax.repository.TaxRateRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** In-memory tax-rate provider backed by rates supplied by a repository. */
public final class CachedTaxRateProvider implements TaxRateProvider {

  private final Map<RateKey, TaxRate> rates;

  /** Creates a provider from the rates supplied by a repository. */
  public CachedTaxRateProvider(TaxRateRepository repository) {
    Objects.requireNonNull(repository, "repository");
    Map<RateKey, TaxRate> indexed = new HashMap<>();
    for (TaxRate taxRate : repository.findAll()) {
      Objects.requireNonNull(taxRate, "taxRate");
      RateKey key = new RateKey(taxRate.country(), taxRate.subdivision(), taxRate.productType());
      if (indexed.putIfAbsent(key, taxRate) != null) {
        throw new IllegalArgumentException("duplicate tax-rate jurisdiction: " + key);
      }
    }
    rates = Map.copyOf(indexed);
  }

  @Override
  public TaxRate getRate(
      Country country, @Nullable CountrySubdivision subdivision, ProductType productType) {
    Objects.requireNonNull(country, "country");
    Objects.requireNonNull(productType, "productType");
    TaxRate taxRate = rates.get(new RateKey(country, subdivision, productType));
    if (taxRate == null) {
      taxRate = rates.get(new RateKey(country, subdivision, null));
    }
    if (taxRate == null) {
      if (subdivision == null && Countries.UNITED_STATES.getValue().equals(country)) {
        throw new IllegalArgumentException("United States resolution requires a subdivision");
      }
      throw new IllegalArgumentException("no tax rate for jurisdiction: " + country);
    }
    return taxRate;
  }

  private record RateKey(
      Country country,
      @Nullable CountrySubdivision subdivision,
      @Nullable ProductType productType) {}
}
