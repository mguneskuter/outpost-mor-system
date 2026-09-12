package com.outpost.gateway.report.api;

import com.outpost.gateway.report.service.BalanceReportException;
import com.outpost.gateway.report.service.ReportService;
import com.outpost.gateway.security.GatewayPrincipal;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
  public BalanceReportResponse tax(HttpServletRequest httpRequest) {
    return BalanceReportResponse.from(service.tax(principal(httpRequest)));
  }

  /** Returns merchant balances: every merchant for the operator, only itself for a merchant. */
  @GetMapping("/merchant")
  public BalanceReportResponse merchant(HttpServletRequest httpRequest) {
    return BalanceReportResponse.from(service.merchant(principal(httpRequest)));
  }

  private static GatewayPrincipal principal(HttpServletRequest httpRequest) {
    return Objects.requireNonNull(
        (GatewayPrincipal)
            httpRequest.getAttribute(MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE),
        "principal");
  }

  @ExceptionHandler(BalanceReportException.class)
  ResponseEntity<ErrorResponse> controlled(BalanceReportException exception) {
    return ResponseEntity.status(exception.status()).body(new ErrorResponse(exception.code()));
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<ErrorResponse> unexpected(@Nullable RuntimeException ignored) {
    return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR"));
  }

  record ErrorResponse(String code) {}
}
