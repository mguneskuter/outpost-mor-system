package com.outpost.common.iso;

import java.util.regex.Pattern;

/** An immutable country subdivision with a stable surrogate identifier and ISO 3166-2 code. */
public record CountrySubdivision(
    long countrySubdivisionId, Country country, String code, String name) {

  private static final Pattern CODE = Pattern.compile("[A-Z]{2}-[A-Z0-9]+");

  /** Rejects non-positive ids, null countries, malformed or mismatched codes, and blank names. */
  public CountrySubdivision {
    if (countrySubdivisionId <= 0) {
      throw new IllegalArgumentException(
          "countrySubdivisionId must be positive: " + countrySubdivisionId);
    }
    if (country == null) {
      throw new IllegalArgumentException("country must not be null");
    }
    if (code == null
        || code.isBlank()
        || !CODE.matcher(code).matches()
        || !code.startsWith(country.isoCode())) {
      throw new IllegalArgumentException(
          "code must be an uppercase ISO 3166-2 shape for its country: " + code);
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
  }
}
