package com.outpost.ledger.report.repository.mybatis;

import com.outpost.ledger.report.repository.BalanceLine;
import com.outpost.ledger.report.repository.BalanceReportRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for Ledger balance reports. */
@Repository
public class MyBatisBalanceReportRepository implements BalanceReportRepository {
  private final BalanceReportMapper mapper;

  /** Creates an adapter backed by the report mapper. */
  public MyBatisBalanceReportRepository(BalanceReportMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<BalanceLine> findTaxBalances() {
    return mapper.findTaxBalances().stream().map(MyBatisBalanceReportRepository::line).toList();
  }

  @Override
  public List<BalanceLine> findMerchantBalances() {
    return mapper.findMerchantBalances().stream()
        .map(MyBatisBalanceReportRepository::line)
        .toList();
  }

  @Override
  public List<BalanceLine> findMerchantBalancesByMerchantCode(String merchantCode) {
    return mapper.findMerchantBalancesByMerchantCode(merchantCode).stream()
        .map(MyBatisBalanceReportRepository::line)
        .toList();
  }

  private static BalanceLine line(BalanceRow row) {
    return new BalanceLine(row.accountCode(), row.accountName(), row.currency(), row.amount());
  }
}
