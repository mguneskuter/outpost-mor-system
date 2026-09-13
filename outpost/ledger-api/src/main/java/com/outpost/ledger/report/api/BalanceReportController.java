package com.outpost.ledger.report.api;

import com.outpost.accounting.api.BalanceReportApi;
import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.ReportPeriod;
import com.outpost.ledger.report.service.BalanceReportService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.RestController;

/** Serves the Ledger's balance reports. */
@RestController
public final class BalanceReportController implements BalanceReportApi {
  private final BalanceReportService service;

  /** Creates a controller backed by the balance report service. */
  public BalanceReportController(BalanceReportService service) {
    this.service = service;
  }

  @Override
  public BalanceReport platform(LocalDate from, LocalDate to) {
    return service.platform(new ReportPeriod(from, to));
  }

  @Override
  public BalanceReport merchant(String merchantCode, LocalDate from, LocalDate to) {
    return service.merchant(merchantCode, new ReportPeriod(from, to));
  }
}
