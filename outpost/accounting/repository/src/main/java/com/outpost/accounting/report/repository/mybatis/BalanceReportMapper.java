package com.outpost.accounting.report.repository.mybatis;

import com.outpost.accounting.report.RegisterBalance;
import com.outpost.framework.persistence.RegisteredMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** MyBatis queries for the balance reports. */
@RegisteredMapper
public interface BalanceReportMapper {
  /** Reads the registers of every merchant, tax authority, and platform account. */
  List<RegisterBalance> findPlatformRegisterBalances(
      @Param("postedFrom") Instant postedFrom, @Param("postedBefore") Instant postedBefore);

  /** Reads the registers of the merchant account with this code. */
  List<RegisterBalance> findMerchantRegisterBalances(
      @Param("merchantCode") String merchantCode,
      @Param("postedFrom") Instant postedFrom,
      @Param("postedBefore") Instant postedBefore);
}
