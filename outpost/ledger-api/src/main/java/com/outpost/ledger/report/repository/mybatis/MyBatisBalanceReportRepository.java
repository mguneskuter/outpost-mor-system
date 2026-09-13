package com.outpost.ledger.report.repository.mybatis;

import com.outpost.ledger.report.repository.BalanceReportRepository;
import com.outpost.ledger.report.repository.RegisterBalance;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for the balance reports. */
@Repository
public class MyBatisBalanceReportRepository implements BalanceReportRepository {
  private final BalanceReportMapper mapper;

  /** Creates an adapter backed by the report mapper. */
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
