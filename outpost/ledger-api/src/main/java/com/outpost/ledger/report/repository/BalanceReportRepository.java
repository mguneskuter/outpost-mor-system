package com.outpost.ledger.report.repository;

import java.util.List;

/** Reads the balance reports owned by Ledger. */
public interface BalanceReportRepository {
  /** Reads balances held for tax authorities. */
  List<BalanceLine> findTaxBalances();

  /** Reads balances owed to every merchant. */
  List<BalanceLine> findMerchantBalances();

  /** Reads balances owed to the merchant with this account code. */
  List<BalanceLine> findMerchantBalancesByMerchantCode(String merchantCode);
}
