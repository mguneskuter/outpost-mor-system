package com.outpost.common.iso;

import java.util.regex.Pattern;

/** An immutable country with a stable surrogate identifier and ISO 3166-1 alpha-2 code. */
public record Country(long countryId, String isoCode, String name) {

  private static final Pattern ISO_CODE = Pattern.compile("[A-Z]{2}");

  /** Rejects non-positive ids, null or malformed ISO codes, and null or blank names. */
  public Country {
    if (countryId <= 0) {
      throw new IllegalArgumentException("countryId must be positive: " + countryId);
    }
    if (isoCode == null || isoCode.isBlank() || !ISO_CODE.matcher(isoCode).matches()) {
      throw new IllegalArgumentException(
          "isoCode must be exactly two uppercase ASCII letters: " + isoCode);
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
  }
}
