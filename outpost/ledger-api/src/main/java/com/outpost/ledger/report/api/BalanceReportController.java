package com.outpost.ledger.report.api;

import com.outpost.accounting.api.BalanceReportApi;
import com.outpost.accounting.api.BalanceReportResponse;
import com.outpost.ledger.report.service.BalanceReport;
import com.outpost.ledger.report.service.BalanceReportService;
import org.springframework.web.bind.annotation.RestController;

/** Exposes Ledger's tax-authority and merchant balance reports. */
@RestController
public final class BalanceReportController implements BalanceReportApi {
  private final BalanceReportService service;

  /** Creates a controller backed by the balance report service. */
  public BalanceReportController(BalanceReportService service) {
    this.service = service;
  }

  @Override
  public BalanceReportResponse tax() {
    return toResponse(service.tax());
  }

  @Override
  public BalanceReportResponse merchant() {
    return toResponse(service.merchant());
  }

  private static BalanceReportResponse toResponse(BalanceReport report) {
    return new BalanceReportResponse(
        report.accounts().stream()
            .map(
                account ->
                    new BalanceReportResponse.Account(
                        account.accountCode(),
                        account.name(),
                        account.balances().stream()
                            .map(
                                balance ->
                                    new BalanceReportResponse.Balance(
                                        balance.currency(), balance.amount()))
                            .toList()))
            .toList());
  }
}
