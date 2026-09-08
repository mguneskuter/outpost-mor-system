package com.outpost.common.iso;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** The currencies supported by Outpost, each owning exactly one {@link Currency} value. */
public enum Currencies {
  CZK(1L, "CZK", 2),
  DKK(2L, "DKK", 2),
  EUR(3L, "EUR", 2),
  GBP(4L, "GBP", 2),
  HUF(5L, "HUF", 2),
  PLN(6L, "PLN", 2),
  RON(7L, "RON", 2),
  SEK(8L, "SEK", 2),
  USD(9L, "USD", 2);

  private static final Map<String, Currencies> BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  constant -> constant.value().currencyCode(), constant -> constant));

  private final Currency value;

  Currencies(long currencyId, String currencyCode, int exponent) {
    value = new Currency(currencyId, currencyCode, exponent);
  }

  /** Returns the {@link Currency} owned by this constant. */
  public Currency value() {
    return value;
  }

  /** Returns the currency for an exact, case-sensitive code, if any. */
  public static Optional<Currency> fromCurrencyCode(String currencyCode) {
    Objects.requireNonNull(currencyCode, "currencyCode");
    return Optional.ofNullable(BY_CODE.get(currencyCode)).map(Currencies::value);
  }
}
