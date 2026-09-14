package com.outpost.account.configuration.repository;

import com.outpost.account.Account;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.common.iso.Currencies.Currency;
import java.util.Optional;

/** Reads merchant fee terms. */
public interface MerchantFeeConfigurationRepository {
  /** Returns whether the merchant account has fee terms for the currency. */
  boolean hasFeeConfiguration(long merchantAccountId, Currency currency);

  /** Finds the merchant account's fee terms for the currency. */
  Optional<MerchantFeeConfiguration> findMerchantFeeConfigurationByAccountAndCurrency(
      Account merchantAccount, Currency currency);
}
