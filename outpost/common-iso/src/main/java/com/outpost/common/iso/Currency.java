package com.outpost.common.iso;

import java.util.regex.Pattern;

/** An immutable currency with a stable surrogate identifier and ISO 4217 alpha-3 code. */
public record Currency(long currencyId, String currencyCode, int exponent) {

  private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

  /** Rejects non-positive ids, null or malformed codes, and negative exponents. */
  public Currency {
    if (currencyId <= 0) {
      throw new IllegalArgumentException("currencyId must be positive: " + currencyId);
    }
    if (currencyCode == null
        || currencyCode.isBlank()
        || !CURRENCY_CODE.matcher(currencyCode).matches()) {
      throw new IllegalArgumentException(
          "currencyCode must be exactly three uppercase ASCII letters: " + currencyCode);
    }
    if (exponent < 0) {
      throw new IllegalArgumentException("exponent must not be negative: " + exponent);
    }
  }
}
