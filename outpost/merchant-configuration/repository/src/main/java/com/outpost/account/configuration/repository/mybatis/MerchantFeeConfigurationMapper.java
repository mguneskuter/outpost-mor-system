package com.outpost.account.configuration.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis statements for merchant fee terms. */
@RegisteredMapper
public interface MerchantFeeConfigurationMapper {
  /** Returns whether the merchant account has fee terms for the currency code. */
  boolean hasFeeConfiguration(
      @Param("merchantAccountId") long merchantAccountId,
      @Param("currencyCode") String currencyCode);
}
