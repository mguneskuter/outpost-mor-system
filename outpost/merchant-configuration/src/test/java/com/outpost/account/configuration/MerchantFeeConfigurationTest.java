package com.outpost.account.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes.FeeMode;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MerchantFeeConfigurationTest {
  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Currency EUR = Currencies.EUR.value();
  private static final Currency USD = Currencies.USD.value();

  @Test
  void acceptsValidPercentageConfigurationAndPreservesInput() {
    Account merchant = merchant();
    MerchantFeeConfiguration configuration =
        configuration(7L, merchant, EUR, FeeModes.PERCENTAGE.value(), 1000, null);

    assertEquals(7L, configuration.merchantFeeConfigurationId());
    assertEquals(merchant, configuration.account());
    assertEquals(EUR, configuration.currency());
    assertEquals(FeeModes.PERCENTAGE.value(), configuration.feeMode());
    assertEquals(1000, configuration.feeRateBps());
    assertEquals(null, configuration.feeFixed());
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 1001, Integer.MAX_VALUE})
  void rejectsFeeRatesOutsideTheInclusiveBounds(int feeRateBps) {
    assertThrows(
        IllegalArgumentException.class,
        () -> configuration(1L, merchant(), EUR, FeeModes.PERCENTAGE.value(), feeRateBps, null));
  }

  @Test
  void acceptsZeroAndMaximumFeeRates() {
    IntStream.of(0, 1000)
        .forEach(
            feeRateBps ->
                configuration(1L, merchant(), EUR, FeeModes.PERCENTAGE.value(), feeRateBps, null));
  }

  @Test
  void rejectsInvalidIdentityAndReferences() {
    Account merchant = merchant();
    assertThrows(
        IllegalArgumentException.class,
        () -> configuration(0L, merchant, EUR, FeeModes.PERCENTAGE.value(), 1, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> configuration(1L, nonMerchant(), EUR, FeeModes.PERCENTAGE.value(), 1, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> configuration(1L, merchant, EUR, FeeModes.PERCENTAGE.value(), 1, new Amount(USD, 1)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            configuration(
                1L, merchant, EUR, FeeModes.PERCENTAGE_PLUS_FIXED.value(), 1, new Amount(USD, 1)));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullRequiredValues() {
    Account merchant = merchant();
    assertThrows(
        IllegalArgumentException.class,
        () -> configuration(1L, null, EUR, FeeModes.PERCENTAGE.value(), 1, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> configuration(1L, merchant, null, FeeModes.PERCENTAGE.value(), 1, null));
    assertThrows(
        IllegalArgumentException.class, () -> configuration(1L, merchant, EUR, null, 1, null));
  }

  @Test
  @SuppressWarnings("NullAway")
  void enforcesModeSpecificFixedFeeRules() {
    Account merchant = merchant();
    Amount positiveFixed = new Amount(EUR, 25);
    Amount negativeFixed = new Amount(EUR, -1);

    assertThrows(
        IllegalArgumentException.class,
        () -> configuration(1L, merchant, EUR, FeeModes.PERCENTAGE.value(), 1, positiveFixed));
    assertThrows(
        IllegalArgumentException.class,
        () -> configuration(1L, merchant, EUR, FeeModes.PERCENTAGE_PLUS_FIXED.value(), 1, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            configuration(
                1L, merchant, EUR, FeeModes.PERCENTAGE_PLUS_FIXED.value(), 1, negativeFixed));
    configuration(1L, merchant, EUR, FeeModes.PERCENTAGE_PLUS_FIXED.value(), 1, new Amount(EUR, 0));
    configuration(1L, merchant, EUR, FeeModes.PERCENTAGE_PLUS_FIXED.value(), 1, positiveFixed);
  }

  @SuppressWarnings("NullAway")
  private static MerchantFeeConfiguration configuration(
      long merchantFeeConfigurationId,
      @Nullable Account account,
      @Nullable Currency currency,
      @Nullable FeeMode feeMode,
      int feeRateBps,
      @Nullable Amount feeFixed) {
    return new MerchantFeeConfiguration(
        merchantFeeConfigurationId, account, currency, feeMode, feeRateBps, feeFixed);
  }

  private static Account merchant() {
    Account root =
        Account.of(1L, AccountTypes.ROOT.value(), "ROOT", "Root", true, CREATED_AT, null);
    return Account.of(
        2L, AccountTypes.MERCHANT.value(), "MERCHANT", "Merchant", true, CREATED_AT, root);
  }

  private static Account nonMerchant() {
    Account root =
        Account.of(3L, AccountTypes.ROOT.value(), "ROOT", "Root", true, CREATED_AT, null);
    return Account.of(4L, AccountTypes.PSP.value(), "PSP", "Psp", true, CREATED_AT, root);
  }
}
