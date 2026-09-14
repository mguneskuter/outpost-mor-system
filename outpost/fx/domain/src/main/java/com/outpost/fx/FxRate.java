package com.outpost.fx;

import com.outpost.common.iso.Currencies.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** An external exchange rate for one ordered currency pair on one date. */
public record FxRate(
    long fxRateId,
    Currency baseCurrency,
    Currency quoteCurrency,
    LocalDate rateDate,
    BigDecimal rate,
    String source) {

  /** Validates the identity, positive rate, and non-blank source. */
  public FxRate {
    if (fxRateId <= 0) {
      throw new IllegalArgumentException("fxRateId must be positive: " + fxRateId);
    }
    Objects.requireNonNull(baseCurrency, "baseCurrency");
    Objects.requireNonNull(quoteCurrency, "quoteCurrency");
    Objects.requireNonNull(rateDate, "rateDate");
    Objects.requireNonNull(rate, "rate");
    Objects.requireNonNull(source, "source");
    if (baseCurrency.equals(quoteCurrency)) {
      throw new IllegalArgumentException("baseCurrency must differ from quoteCurrency");
    }
    if (rate.signum() <= 0) {
      throw new IllegalArgumentException("rate must be positive: " + rate);
    }
    if (source.isBlank()) {
      throw new IllegalArgumentException("source must not be blank");
    }
  }
}
