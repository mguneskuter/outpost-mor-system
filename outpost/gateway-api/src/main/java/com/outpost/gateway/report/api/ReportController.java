package com.outpost.gateway.report.api;

import com.outpost.accounting.report.BalanceReport;
import com.outpost.gateway.report.service.ReportService;
import com.outpost.gateway.security.GatewayPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Builds a balance report for the caller and serves the reports built. */
@RestController
public final class ReportController {
  private final ReportService service;

  /** Creates a controller backed by the report service. */
  public ReportController(ReportService service) {
    this.service = service;
  }

  /** Builds the caller's report over the period and answers the URL it can be read at. */
  @GetMapping("/v1/report")
  public ReportLinkResponse createReport(
      GatewayPrincipal principal,
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    UUID reportId = service.createReport(principal, from, to);
    return new ReportLinkResponse(
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/v1/report/{reportId}")
            .buildAndExpand(reportId)
            .toUriString());
  }

  /** Answers a stored report. */
  @GetMapping("/v1/report/{reportId}")
  public BalanceReport findReport(
      GatewayPrincipal principal, @PathVariable("reportId") UUID reportId) {
    return service.findReport(reportId);
  }
}
