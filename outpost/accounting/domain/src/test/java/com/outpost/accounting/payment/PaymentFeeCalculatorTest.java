package com.outpost.accounting.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PaymentFeeCalculatorTest {
  private static final Account MERCHANT =
      Account.of(
          500L,
          AccountTypes.MERCHANT.getValue(),
          "M500",
          "Merchant",
          true,
          Instant.EPOCH,
          Account.of(
              499L, AccountTypes.ROOT.getValue(), "R499", "Root", true, Instant.EPOCH, null));

  private final PaymentFeeCalculator calculator = new PaymentFeeCalculator();

  @Test
  void computesPercentageFeeWithHalfEvenRounding() {
    // 250 bps of 10_050 = 251.25, not a tie: rounds to the nearer 251 (the exact tie case is
    // exercised below).
    MerchantFeeConfiguration configuration =
        configuration(FeeModes.PERCENTAGE.getValue(), 250, null);

    Amount fee =
        calculator.calculate(new Amount(Currencies.EUR.getValue(), 10_050L), configuration);

    assertEquals(new Amount(Currencies.EUR.getValue(), 251L), fee);
  }

  @Test
  void roundsAnExactHalfToTheNearestEvenMinorUnit() {
    // 250 bps of 10_100 = 252.5 exactly; half-even rounds to 252 (the even neighbour).
    MerchantFeeConfiguration configuration =
        configuration(FeeModes.PERCENTAGE.getValue(), 250, null);

    Amount fee =
        calculator.calculate(new Amount(Currencies.EUR.getValue(), 10_100L), configuration);

    assertEquals(new Amount(Currencies.EUR.getValue(), 252L), fee);
  }

  @Test
  void addsTheFixedComponentUnderPercentagePlusFixed() {
    MerchantFeeConfiguration configuration =
        configuration(
            FeeModes.PERCENTAGE_PLUS_FIXED.getValue(),
            100,
            new Amount(Currencies.EUR.getValue(), 30L));

    Amount fee = calculator.calculate(new Amount(Currencies.EUR.getValue(), 1_000L), configuration);

    // 100 bps of 1_000 = 10, plus the fixed 30 = 40.
    assertEquals(new Amount(Currencies.EUR.getValue(), 40L), fee);
  }

  @Test
  void acceptsFeeExactlyEqualToNet() {
    MerchantFeeConfiguration configuration =
        configuration(
            FeeModes.PERCENTAGE_PLUS_FIXED.getValue(),
            0,
            new Amount(Currencies.EUR.getValue(), 100L));

    Amount fee = calculator.calculate(new Amount(Currencies.EUR.getValue(), 100L), configuration);

    assertEquals(new Amount(Currencies.EUR.getValue(), 100L), fee);
  }

  @Test
  void rejectsFeeAboveNet() {
    MerchantFeeConfiguration configuration =
        configuration(
            FeeModes.PERCENTAGE_PLUS_FIXED.getValue(),
            0,
            new Amount(Currencies.EUR.getValue(), 101L));

    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculate(new Amount(Currencies.EUR.getValue(), 100L), configuration));
  }

  @Test
  void rejectsMismatchedCurrencies() {
    MerchantFeeConfiguration configuration =
        configuration(FeeModes.PERCENTAGE.getValue(), 100, null);

    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculate(new Amount(Currencies.USD.getValue(), 100L), configuration));
  }

  @Test
  void rejectsAnOverflowingFeeSum() {
    MerchantFeeConfiguration configuration =
        configuration(
            FeeModes.PERCENTAGE_PLUS_FIXED.getValue(),
            1,
            new Amount(Currencies.EUR.getValue(), Long.MAX_VALUE));

    assertThrows(
        ArithmeticException.class,
        () ->
            calculator.calculate(
                new Amount(Currencies.EUR.getValue(), Long.MAX_VALUE), configuration));
  }

  private static MerchantFeeConfiguration configuration(
      FeeModes.FeeMode feeMode, int feeRateBps, @Nullable Amount feeFixed) {
    return new MerchantFeeConfiguration(
        1L, MERCHANT, Currencies.EUR.getValue(), feeMode, feeRateBps, feeFixed);
  }
}
