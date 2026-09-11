package com.outpost.gateway.tax.repository.mybatis;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.tax.TaxRate;
import com.outpost.tax.repository.TaxRateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** MyBatis implementation of the tax-rate repository. */
public final class MybatisTaxRateRepository implements TaxRateRepository {

  private final TaxRateProviderRepositoryMapper mapper;

  /** Creates a repository backed by the tax-rate mapper. */
  public MybatisTaxRateRepository(TaxRateProviderRepositoryMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public List<TaxRate> findAll() {
    List<TaxRateEntity> rows = Objects.requireNonNull(mapper.findAll(), "tax-rate rows");
    List<TaxRate> taxRates = new ArrayList<>(rows.size());
    for (TaxRateEntity row : rows) {
      taxRates.add(toTaxRate(Objects.requireNonNull(row, "tax-rate row")));
    }
    return taxRates;
  }

  private static TaxRate toTaxRate(TaxRateEntity row) {
    var country =
        Countries.fromIsoCode(row.countryCode())
            .orElseThrow(
                () -> new IllegalStateException("unknown tax country: " + row.countryCode()));
    CountrySubdivision subdivision = subdivision(row, country);
    return new TaxRate(country, subdivision, row.rate());
  }

  private static @Nullable CountrySubdivision subdivision(
      TaxRateEntity row, com.outpost.common.iso.Countries.Country country) {
    if (row.subdivisionCode() == null) {
      return null;
    }
    CountrySubdivision subdivision =
        CountrySubdivisions.fromCode(country, row.subdivisionCode())
            .orElseThrow(
                () ->
                    new IllegalStateException("unknown tax subdivision: " + row.subdivisionCode()));
    return subdivision;
  }
}
