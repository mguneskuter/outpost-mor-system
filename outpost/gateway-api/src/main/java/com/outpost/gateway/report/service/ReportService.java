package com.outpost.gateway.report.service;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.InvalidReportPeriodException;
import com.outpost.accounting.report.ReportPeriod;
import com.outpost.gateway.report.client.LedgerReportClient;
import com.outpost.gateway.security.GatewayPrincipal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds balance reports scoped to the calling principal and keeps them for reading. A merchant's
 * report covers the account its key authenticated, never an account the request names; the
 * operator's report covers every merchant, tax authority, and platform account.
 */
public final class ReportService {
  private final LedgerReportClient ledger;
  private final AccountRepository accounts;
  private final GeneratedReports reports;

  /** Creates a service over the Ledger report client, the accounts, and the report store. */
  public ReportService(
      LedgerReportClient ledger, AccountRepository accounts, GeneratedReports reports) {
    this.ledger = ledger;
    this.accounts = accounts;
    this.reports = reports;
  }

  /**
   * Builds the caller's report over the period and returns the identifier it can be read under.
   *
   * @throws BalanceReportException {@code INVALID_REPORT_PERIOD} before the Ledger is called when
   *     the dates do not form a valid period; {@code MERCHANT_NOT_FOUND} when the merchant key's
   *     account is not an active merchant
   */
  public UUID createReport(GatewayPrincipal principal, LocalDate from, LocalDate to) {
    ReportPeriod period;
    try {
      period = new ReportPeriod(from, to);
    } catch (InvalidReportPeriodException invalid) {
      throw new BalanceReportException(BalanceReportErrorCodes.INVALID_REPORT_PERIOD);
    }
    BalanceReport report =
        switch (principal.type()) {
          case OPERATOR -> ledger.platformReport(period);
          case MERCHANT -> ledger.merchantReport(merchantCode(principal), period);
        };
    return reports.add(report);
  }

  /**
   * Reads a stored report; any authenticated key may read any stored report.
   *
   * @throws BalanceReportException {@code REPORT_NOT_FOUND} when no report is stored under the
   *     identifier
   */
  public BalanceReport findReport(UUID reportId) {
    return reports
        .find(reportId)
        .orElseThrow(() -> new BalanceReportException(BalanceReportErrorCodes.REPORT_NOT_FOUND));
  }

  private String merchantCode(GatewayPrincipal principal) {
    return accounts
        .findAccountById(principal.accountId())
        .filter(
            account ->
                account.isActive()
                    && account.getAccountType().equals(AccountTypes.MERCHANT.getValue()))
        .map(Account::getCode)
        .orElseThrow(() -> new BalanceReportException(BalanceReportErrorCodes.MERCHANT_NOT_FOUND));
  }
}
