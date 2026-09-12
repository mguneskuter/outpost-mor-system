package com.outpost.gateway.report.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** MyBatis statements for balance report scoping. */
@RegisteredMapper
public interface ReportMapper {
  /** Finds an active merchant's account code. */
  @Nullable String findMerchantCode(@Param("accountId") long accountId);
}
