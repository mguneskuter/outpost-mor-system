package com.outpost.ledger.report.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** MyBatis queries for Ledger balance reports. */
@RegisteredMapper
public interface BalanceReportMapper {
  /** Reads balances on tax-authority TAX_PAYABLE registers. */
  List<BalanceRow> findTaxBalances();

  /** Reads balances on merchant MERCHANT_PAYABLE registers. */
  List<BalanceRow> findMerchantBalances();
}
