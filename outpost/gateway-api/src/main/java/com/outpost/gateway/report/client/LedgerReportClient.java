package com.outpost.gateway.report.client;

/** Reads Ledger's balance reports. */
public interface LedgerReportClient {
  /** Reads balances held for tax authorities. */
  BalanceReport taxBalances();

  /** Reads balances owed to every merchant. */
  BalanceReport merchantBalances();

  /** Reads balances owed to the merchant with this account code. */
  BalanceReport merchantBalances(String merchantCode);
}
