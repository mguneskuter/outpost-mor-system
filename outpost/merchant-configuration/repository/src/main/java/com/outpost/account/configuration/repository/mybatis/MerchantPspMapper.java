package com.outpost.account.configuration.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis statements for the PSPs enabled for merchants. */
@RegisteredMapper
public interface MerchantPspMapper {
  /** Returns whether the PSP account is enabled for the merchant account. */
  boolean isPspEnabled(
      @Param("merchantAccountId") long merchantAccountId, @Param("pspAccountId") long pspAccountId);
}
