package com.outpost.gateway.report.api;

import com.outpost.gateway.report.service.ReportService;
import com.outpost.gateway.security.GatewayPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes Ledger's tax-authority and merchant balance reports, scoped to the caller. */
@RestController
@RequestMapping("/v1/report/balance")
public final class BalanceReportController {
  private final ReportService service;

  /** Creates a controller backed by the report service. */
  public BalanceReportController(ReportService service) {
    this.service = service;
  }

  /** Returns tax-authority balances for the operator. */
  @GetMapping("/tax")
  public BalanceReportResponse tax(GatewayPrincipal principal) {
    return BalanceReportResponse.from(service.tax(principal));
  }

  /** Returns merchant balances: every merchant for the operator, only itself for a merchant. */
  @GetMapping("/merchant")
  public BalanceReportResponse merchant(GatewayPrincipal principal) {
    return BalanceReportResponse.from(service.merchant(principal));
  }
}
