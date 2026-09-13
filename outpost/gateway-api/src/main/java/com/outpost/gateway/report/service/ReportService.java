package com.outpost.gateway.report.service;

import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.InvalidReportPeriodException;
import com.outpost.accounting.report.ReportPeriod;
import com.outpost.gateway.report.client.LedgerReportClient;
import com.outpost.gateway.report.repository.ReportRepository;
import com.outpost.gateway.security.GatewayPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Builds balance reports scoped to the calling principal and keeps them for reading. A merchant's
 * report covers the account its key authenticated, never an account the request names; the
 * operator's report covers every merchant, tax authority, and platform account.
 */
public final class ReportService {
  private final LedgerReportClient ledger;
  private final ReportRepository repository;
  private final GeneratedReports reports;

  /** Creates a service over the Ledger report client, the merchant lookup, and the report store. */
  public ReportService(
      LedgerReportClient ledger, ReportRepository repository, GeneratedReports reports) {
    this.ledger = ledger;
    this.repository = repository;
    this.reports = reports;
  }

  /**
   * Builds the caller's report over the period and returns the identifier it can be read under.
   *
   * @throws BalanceReportException 400 INVALID_REPORT_PERIOD before the Ledger is called when the
   *     dates do not form a valid period; 401 MERCHANT_NOT_FOUND when the merchant key's account is
   *     not active
   */
  public UUID createReport(GatewayPrincipal principal, LocalDate from, LocalDate to) {
    ReportPeriod period;
    try {
      period = new ReportPeriod(from, to);
    } catch (InvalidReportPeriodException invalid) {
      throw new BalanceReportException(HttpStatus.BAD_REQUEST.value(), "INVALID_REPORT_PERIOD");
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
   * @throws BalanceReportException 404 REPORT_NOT_FOUND when no report is stored under the
   *     identifier
   */
  public BalanceReport findReport(UUID reportId) {
    return reports
        .find(reportId)
        .orElseThrow(
            () -> new BalanceReportException(HttpStatus.NOT_FOUND.value(), "REPORT_NOT_FOUND"));
  }

  private String merchantCode(GatewayPrincipal principal) {
    return repository
        .findMerchantCode(principal.accountId())
        .orElseThrow(
            () ->
                new BalanceReportException(HttpStatus.UNAUTHORIZED.value(), "MERCHANT_NOT_FOUND"));
  }
}
