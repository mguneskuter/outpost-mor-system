package com.outpost.account.configuration.repository.mybatis;

import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** MyBatis statements for merchant fee terms. Package-private, like the stored form it returns. */
interface MerchantFeeConfigurationMapper {
  /** Returns whether the merchant account has fee terms for the currency code. */
  boolean hasFeeConfiguration(
      @Param("merchantAccountId") long merchantAccountId,
      @Param("currencyCode") String currencyCode);

  @Nullable MerchantFeeConfiguration findMerchantFeeConfigurationByAccountAndCurrency(
      @Param("merchantAccountId") long merchantAccountId,
      @Param("currencyCode") String currencyCode);
}
