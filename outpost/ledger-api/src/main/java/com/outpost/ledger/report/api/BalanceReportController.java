package com.outpost.ledger.report.api;

import com.outpost.ledger.report.service.BalanceReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes Ledger's tax-authority and merchant balance reports. */
@RestController
@RequestMapping("/v1/report/balance")
public final class BalanceReportController {
  private final BalanceReportService service;

  /** Creates a controller backed by the balance report service. */
  public BalanceReportController(BalanceReportService service) {
    this.service = service;
  }

  /** Returns tax-authority balances. */
  @GetMapping("/tax")
  public BalanceReportResponse tax() {
    return BalanceReportResponse.from(service.tax());
  }

  /** Returns merchant balances. */
  @GetMapping("/merchant")
  public BalanceReportResponse merchant() {
    return BalanceReportResponse.from(service.merchant());
  }
}
