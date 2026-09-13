package com.outpost.gateway.report.client;

import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.ReportPeriod;

/** Reads Ledger's balance reports. */
public interface LedgerReportClient {
  /** Reads the platform's report: every merchant, tax authority, and platform account. */
  BalanceReport platformReport(ReportPeriod period);

  /** Reads the report of the merchant with this account code. */
  BalanceReport merchantReport(String merchantCode, ReportPeriod period);
}
