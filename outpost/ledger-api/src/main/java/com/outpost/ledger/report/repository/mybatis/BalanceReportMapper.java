package com.outpost.ledger.report.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** MyBatis queries for Ledger balance reports. */
@RegisteredMapper
public interface BalanceReportMapper {
  /** Reads balances on tax-authority TAX_PAYABLE registers. */
  List<BalanceRow> findTaxBalances();

  /** Reads balances on every merchant's MERCHANT_PAYABLE registers. */
  List<BalanceRow> findMerchantBalances();

  /** Reads balances on the MERCHANT_PAYABLE registers of the merchant with this account code. */
  List<BalanceRow> findMerchantBalancesByMerchantCode(@Param("merchantCode") String merchantCode);
}
