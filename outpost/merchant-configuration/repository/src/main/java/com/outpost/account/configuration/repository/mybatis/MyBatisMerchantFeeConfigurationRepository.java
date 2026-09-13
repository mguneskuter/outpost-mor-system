package com.outpost.account.configuration.repository.mybatis;

import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.common.iso.Currencies.Currency;

/** Reads merchant fee terms from {@code merchant_fee_configuration}. */
public final class MyBatisMerchantFeeConfigurationRepository
    implements MerchantFeeConfigurationRepository {
  private final MerchantFeeConfigurationMapper mapper;

  /** Creates a repository over its mapper. */
  public MyBatisMerchantFeeConfigurationRepository(MerchantFeeConfigurationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public boolean hasFeeConfiguration(long merchantAccountId, Currency currency) {
    return mapper.hasFeeConfiguration(merchantAccountId, currency.getCurrencyCode());
  }
}
