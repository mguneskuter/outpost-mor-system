package com.outpost.account.configuration.repository;

import com.outpost.common.iso.Currencies.Currency;

/** Reads merchant fee terms. */
public interface MerchantFeeConfigurationRepository {
  /** Returns whether the merchant account has fee terms for the currency. */
  boolean hasFeeConfiguration(long merchantAccountId, Currency currency);
}
