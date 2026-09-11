package com.outpost.fx;

import com.outpost.common.iso.Currencies.Currency;
import java.util.Objects;

/** The platform fee in basis points for one ordered currency pair. */
public record FxFee(long fxFeeId, Currency baseCurrency, Currency quoteCurrency, int feeRateBps) {

  /** Validates the identity, ordered pair, and non-negative fee. */
  public FxFee {
    if (fxFeeId <= 0) {
      throw new IllegalArgumentException("fxFeeId must be positive: " + fxFeeId);
    }
    Objects.requireNonNull(baseCurrency, "baseCurrency");
    Objects.requireNonNull(quoteCurrency, "quoteCurrency");
    if (baseCurrency.equals(quoteCurrency)) {
      throw new IllegalArgumentException("baseCurrency must differ from quoteCurrency");
    }
    if (feeRateBps < 0) {
      throw new IllegalArgumentException("feeRateBps must not be negative: " + feeRateBps);
    }
  }
}
