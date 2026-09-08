package com.outpost.payment.common;

import com.outpost.common.iso.Currencies.Currency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** A signed quantity of minor currency units. */
public record Amount(Currency currency, long quantity) {
  /** Creates an amount with a non-null currency. */
  public Amount {
    Objects.requireNonNull(currency, "currency");
  }

  /** Adds a same-currency amount without allowing overflow. */
  public Amount plus(Amount other) {
    requireSameCurrency(other);
    return new Amount(currency, Math.addExact(quantity, other.quantity));
  }

  /** Subtracts a same-currency amount without allowing overflow. */
  public Amount minus(Amount other) {
    requireSameCurrency(other);
    return new Amount(currency, Math.subtractExact(quantity, other.quantity));
  }

  /** Negates this amount without allowing overflow. */
  public Amount negated() {
    return new Amount(currency, Math.negateExact(quantity));
  }

  /** Multiplies this amount and rounds the minor-unit result with HALF_EVEN. */
  public Amount multipliedBy(BigDecimal multiplier) {
    Objects.requireNonNull(multiplier, "multiplier");
    return new Amount(currency, roundedLong(BigDecimal.valueOf(quantity).multiply(multiplier)));
  }

  /** Divides this amount and rounds the minor-unit result with HALF_EVEN. */
  public Amount dividedBy(BigDecimal divisor) {
    Objects.requireNonNull(divisor, "divisor");
    if (divisor.signum() == 0) {
      throw new ArithmeticException("divisor must not be zero");
    }
    return new Amount(
        currency,
        BigDecimal.valueOf(quantity).divide(divisor, 0, RoundingMode.HALF_EVEN).longValueExact());
  }

  private void requireSameCurrency(Amount other) {
    Objects.requireNonNull(other, "other");
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("amounts must use the same currency");
    }
  }

  private static long roundedLong(BigDecimal quantity) {
    return quantity.setScale(0, RoundingMode.HALF_EVEN).longValueExact();
  }
}
