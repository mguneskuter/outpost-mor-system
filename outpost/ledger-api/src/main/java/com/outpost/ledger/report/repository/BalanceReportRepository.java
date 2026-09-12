package com.outpost.ledger.report.repository;

import java.util.List;

/** Reads the two balance reports owned by Ledger. */
public interface BalanceReportRepository {
  /** Reads balances held for tax authorities. */
  List<BalanceLine> findTaxBalances();

  /** Reads balances owed to merchants. */
  List<BalanceLine> findMerchantBalances();
}
