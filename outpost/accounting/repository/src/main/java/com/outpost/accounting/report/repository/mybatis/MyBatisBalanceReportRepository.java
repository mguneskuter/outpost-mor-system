package com.outpost.accounting.report.repository.mybatis;

import com.outpost.accounting.report.RegisterBalance;
import com.outpost.accounting.report.repository.BalanceReportRepository;
import java.time.Instant;
import java.util.List;

/** Reads register balances from the journal tables. */
public final class MyBatisBalanceReportRepository implements BalanceReportRepository {
  private final BalanceReportMapper mapper;

  /** Creates a repository over the mapper. */
  public MyBatisBalanceReportRepository(BalanceReportMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<RegisterBalance> findPlatformRegisterBalances(
      Instant postedFrom, Instant postedBefore) {
    return mapper.findPlatformRegisterBalances(postedFrom, postedBefore);
  }

  @Override
  public List<RegisterBalance> findMerchantRegisterBalances(
      String merchantCode, Instant postedFrom, Instant postedBefore) {
    return mapper.findMerchantRegisterBalances(merchantCode, postedFrom, postedBefore);
  }
}
