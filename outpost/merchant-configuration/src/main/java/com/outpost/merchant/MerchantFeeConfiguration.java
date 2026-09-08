package com.outpost.merchant;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.merchant.FeeModes.FeeMode;
import com.outpost.payment.common.Amount;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable merchant fee terms for one account and currency. */
public record MerchantFeeConfiguration(
    long merchantFeeConfigurationId,
    Account account,
    Currency currency,
    FeeMode feeMode,
    int feeRateBps,
    @Nullable Amount feeFixed) {
  /** Creates fee terms after validating their persisted-field invariants. */
  public MerchantFeeConfiguration {
    if (merchantFeeConfigurationId <= 0) {
      throw new IllegalArgumentException(
          "merchantFeeConfigurationId must be positive: " + merchantFeeConfigurationId);
    }
    if (account == null) {
      throw new IllegalArgumentException("account must not be null");
    }
    if (!account.accountType().equals(AccountTypes.MERCHANT.value())) {
      throw new IllegalArgumentException("account must have the MERCHANT account type");
    }
    if (currency == null) {
      throw new IllegalArgumentException("currency must not be null");
    }
    if (feeMode == null) {
      throw new IllegalArgumentException("feeMode must not be null");
    }
    if (feeRateBps < 0 || feeRateBps > 1000) {
      throw new IllegalArgumentException("feeRateBps must be between 0 and 1000");
    }
    if (feeMode.equals(FeeModes.PERCENTAGE.value()) && feeFixed != null) {
      throw new IllegalArgumentException("PERCENTAGE must not have a fixed fee");
    }
    if (feeMode.equals(FeeModes.PERCENTAGE_PLUS_FIXED.value())
        && (feeFixed == null || feeFixed.quantity() < 0)) {
      throw new IllegalArgumentException(
          "PERCENTAGE_PLUS_FIXED must have a non-negative fixed fee");
    }
    if (feeFixed != null && !Objects.equals(feeFixed.currency(), currency)) {
      throw new IllegalArgumentException("fixed fee must use the configuration currency");
    }
  }
}
