package com.outpost.accounting.payment;

import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.payment.common.Amount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Computes the fee owed on a payment's net amount from the merchant's fee terms.
 *
 * <p>{@code percentageFee = roundHalfEven(net * feeRateBps / 10_000)}, and {@code fee =
 * percentageFee + feeFixed} when the configuration is {@link FeeModes#PERCENTAGE_PLUS_FIXED}. The
 * fee is never clamped: a fee above net is rejected rather than capped.
 */
public final class PaymentFeeCalculator {
  private static final BigDecimal BASIS_POINT_DIVISOR = BigDecimal.valueOf(10_000);

  /**
   * Returns the fee owed on {@code net} under {@code configuration}.
   *
   * @throws IllegalArgumentException if the currencies differ, or the computed fee is negative or
   *     exceeds net
   * @throws ArithmeticException if the percentage and fixed fee components overflow a long
   */
  public Amount calculate(Amount net, MerchantFeeConfiguration configuration) {
    Objects.requireNonNull(net, "net");
    Objects.requireNonNull(configuration, "configuration");
    if (!net.currency().equals(configuration.currency())) {
      throw new IllegalArgumentException("net amount must use the configuration currency");
    }
    if (net.quantity() < 0) {
      throw new IllegalArgumentException("net must not be negative: " + net);
    }
    long percentageFee =
        BigDecimal.valueOf(net.quantity())
            .multiply(BigDecimal.valueOf(configuration.feeRateBps()))
            .divide(BASIS_POINT_DIVISOR, 0, RoundingMode.HALF_EVEN)
            .longValueExact();
    long fixedFee = 0L;
    if (configuration.feeMode().equals(FeeModes.PERCENTAGE_PLUS_FIXED.getValue())) {
      Amount feeFixed = configuration.feeFixed();
      if (feeFixed == null) {
        throw new IllegalArgumentException(
            "PERCENTAGE_PLUS_FIXED configuration must carry a fixed fee");
      }
      fixedFee = feeFixed.quantity();
    }
    long feeQuantity = Math.addExact(percentageFee, fixedFee);
    if (feeQuantity < 0 || feeQuantity > net.quantity()) {
      throw new IllegalArgumentException(
          "fee must be between 0 and net (" + net.quantity() + "): " + feeQuantity);
    }
    return new Amount(net.currency(), feeQuantity);
  }
}
