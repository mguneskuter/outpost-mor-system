package com.outpost.gateway.report.repository.mybatis;

import com.outpost.gateway.report.repository.ReportRepository;
import java.util.Optional;

/** MyBatis persistence adapter for balance report scoping. */
public final class MyBatisReportRepository implements ReportRepository {
  private final ReportMapper mapper;

  /** Creates an adapter backed by the report mapper. */
  public MyBatisReportRepository(ReportMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<String> findMerchantCode(long accountId) {
    return Optional.ofNullable(mapper.findMerchantCode(accountId));
  }
}
