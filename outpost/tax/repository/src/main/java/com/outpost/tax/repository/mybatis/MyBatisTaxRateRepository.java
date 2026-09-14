package com.outpost.tax.repository.mybatis;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.tax.repository.TaxRateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** MyBatis implementation of the tax-rate repository. */
public final class MyBatisTaxRateRepository implements TaxRateRepository {

  private final TaxRateMapper mapper;

  /** Creates a repository backed by the tax-rate mapper. */
  public MyBatisTaxRateRepository(TaxRateMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public List<com.outpost.tax.TaxRate> findTaxRates() {
    List<TaxRate> rows = Objects.requireNonNull(mapper.findTaxRates(), "tax-rate rows");
    List<com.outpost.tax.TaxRate> taxRates = new ArrayList<>(rows.size());
    for (TaxRate row : rows) {
      taxRates.add(toTaxRate(Objects.requireNonNull(row, "tax-rate row")));
    }
    return taxRates;
  }

  private static com.outpost.tax.TaxRate toTaxRate(TaxRate row) {
    var country =
        Countries.fromIsoCode(row.countryCode())
            .orElseThrow(
                () -> new IllegalStateException("unknown tax country: " + row.countryCode()));
    CountrySubdivision subdivision = subdivision(row, country);
    return new com.outpost.tax.TaxRate(country, subdivision, productType(row), row.rate());
  }

  private static @Nullable ProductType productType(TaxRate row) {
    if (row.productTypeCode() == null) {
      return null;
    }
    return ProductTypes.fromCode(row.productTypeCode())
        .orElseThrow(
            () -> new IllegalStateException("unknown tax product type: " + row.productTypeCode()));
  }

  private static @Nullable CountrySubdivision subdivision(
      TaxRate row, com.outpost.common.iso.Countries.Country country) {
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
