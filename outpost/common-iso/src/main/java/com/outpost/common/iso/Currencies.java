package com.outpost.common.iso;

import com.outpost.platform.staticdata.StaticData;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The currencies supported by Outpost, each owning exactly one {@link Currency} value. */
@StaticData
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

  @SuppressWarnings("Immutable")
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

  /** Immutable currency value owned by one {@link Currencies} constant. */
  public static final class Currency {

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    private final long currencyId;
    private final String currencyCode;
    private final int exponent;

    private Currency(long currencyId, String currencyCode, int exponent) {
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
      this.currencyId = currencyId;
      this.currencyCode = currencyCode;
      this.exponent = exponent;
    }

    /** Returns the stable currency identifier. */
    public long currencyId() {
      return currencyId;
    }

    /** Returns the exact ISO currency code. */
    public String currencyCode() {
      return currencyCode;
    }

    /** Returns the number of minor units per currency unit. */
    public int exponent() {
      return exponent;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Currency that)) {
        return false;
      }
      return currencyId == that.currencyId
          && currencyCode.equals(that.currencyCode)
          && exponent == that.exponent;
    }

    @Override
    public int hashCode() {
      return Objects.hash(currencyId, currencyCode, exponent);
    }

    @Override
    public String toString() {
      return "Currency{currencyId="
          + currencyId
          + ", currencyCode="
          + currencyCode
          + ", exponent="
          + exponent
          + '}';
    }
  }
}
