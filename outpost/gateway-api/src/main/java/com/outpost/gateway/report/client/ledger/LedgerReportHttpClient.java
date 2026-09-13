package com.outpost.gateway.report.client.ledger;

import com.outpost.accounting.api.BalanceReportApi;
import com.outpost.accounting.api.BalanceReportResponse;
import com.outpost.gateway.report.client.BalanceReport;
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
  public BalanceReport taxBalances() {
    return fetch(balanceReportApi::tax);
  }

  @Override
  public BalanceReport merchantBalances() {
    return fetch(balanceReportApi::merchant);
  }

  @Override
  public BalanceReport merchantBalances(String merchantCode) {
    return fetch(() -> balanceReportApi.merchant(merchantCode));
  }

  private static BalanceReport fetch(Supplier<BalanceReportResponse> report) {
    try {
      return toBalanceReport(report.get());
    } catch (RuntimeException exception) {
      throw new LedgerReportClientException("Ledger balance report request failed", exception);
    }
  }

  private static BalanceReport toBalanceReport(BalanceReportResponse response) {
    return new BalanceReport(
        response.accounts().stream()
            .map(
                account ->
                    new BalanceReport.Account(
                        account.accountCode(),
                        account.name(),
                        account.balances().stream()
                            .map(
                                balance ->
                                    new BalanceReport.Balance(balance.currency(), balance.amount()))
                            .toList()))
            .toList());
  }
}
