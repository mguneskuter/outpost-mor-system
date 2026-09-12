package com.outpost.gateway.report.service;

import com.outpost.gateway.report.client.BalanceReport;
import com.outpost.gateway.report.client.LedgerReportClient;
import com.outpost.gateway.report.repository.ReportRepository;
import com.outpost.gateway.security.GatewayPrincipal;
import org.springframework.http.HttpStatus;

/** Scopes Ledger's balance reports to the calling principal. */
public final class ReportService {
  private final LedgerReportClient ledger;
  private final ReportRepository repository;

  /** Creates a service backed by the Ledger report client and merchant lookup. */
  public ReportService(LedgerReportClient ledger, ReportRepository repository) {
    this.ledger = ledger;
    this.repository = repository;
  }

  /** Returns tax-authority balances, restricted to the operator. */
  public BalanceReport tax(GatewayPrincipal principal) {
    requireOperator(principal);
    return ledger.taxBalances();
  }

  /** Returns merchant balances: every merchant for the operator, only itself for a merchant. */
  public BalanceReport merchant(GatewayPrincipal principal) {
    BalanceReport report = ledger.merchantBalances();
    if (principal.type() == GatewayPrincipal.Type.OPERATOR) {
      return report;
    }
    String merchantCode =
        repository
            .findMerchantCode(principal.accountId())
            .orElseThrow(
                () ->
                    new BalanceReportException(
                        HttpStatus.UNAUTHORIZED.value(), "MERCHANT_NOT_FOUND"));
    return new BalanceReport(
        report.accounts().stream()
            .filter(account -> account.accountCode().equals(merchantCode))
            .toList());
  }

  private static void requireOperator(GatewayPrincipal principal) {
    if (principal.type() != GatewayPrincipal.Type.OPERATOR) {
      throw new BalanceReportException(HttpStatus.FORBIDDEN.value(), "OPERATOR_REQUIRED");
    }
  }
}
