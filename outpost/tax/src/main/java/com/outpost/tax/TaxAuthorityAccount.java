package com.outpost.tax;

import com.outpost.common.iso.Countries.Country;
import java.util.Objects;

/** The tax-authority account assigned to one country. */
public record TaxAuthorityAccount(Country country, long accountId) {

  /** Validates the country and positive account identifier. */
  public TaxAuthorityAccount {
    Objects.requireNonNull(country, "country");
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive: " + accountId);
    }
  }
}
