package com.outpost.gateway.report.client.ledger;

import com.outpost.accounting.api.BalanceReportApi;
import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.ReportPeriod;
import com.outpost.gateway.report.client.LedgerReportClient;
import java.util.function.Supplier;

/** Reads Ledger's balance reports through Ledger's HTTP contract. */
public final class LedgerReportHttpClient implements LedgerReportClient {
  private final BalanceReportApi balanceReportApi;

  /** Creates a Ledger report client over the balance report contract proxy. */
  public LedgerReportHttpClient(BalanceReportApi balanceReportApi) {
    this.balanceReportApi = balanceReportApi;
  }

  @Override
  public BalanceReport platformReport(ReportPeriod period) {
    return fetch(() -> balanceReportApi.platform(period.from(), period.to()));
  }

  @Override
  public BalanceReport merchantReport(String merchantCode, ReportPeriod period) {
    return fetch(() -> balanceReportApi.merchant(merchantCode, period.from(), period.to()));
  }

  private static BalanceReport fetch(Supplier<BalanceReport> report) {
    try {
      return report.get();
    } catch (RuntimeException exception) {
      throw new LedgerReportClientException("Ledger balance report request failed", exception);
    }
  }
}
