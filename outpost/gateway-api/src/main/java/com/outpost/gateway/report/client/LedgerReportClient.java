package com.outpost.gateway.report.client;

/** Reads Ledger's balance reports. */
public interface LedgerReportClient {
  /** Reads balances held for tax authorities. */
  BalanceReport taxBalances();

  /** Reads balances owed to merchants. */
  BalanceReport merchantBalances();
}
