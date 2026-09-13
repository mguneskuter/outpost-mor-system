package com.outpost.accounting.api;

import com.outpost.accounting.report.BalanceReport;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Ledger's balance report routes. Both dates are inclusive calendar days in UTC, and the period
 * they span is at most thirty days; a longer or reversed period answers 400 INVALID_REQUEST.
 */
@HttpExchange("/v1/report/balance")
public interface BalanceReportApi {
  /** Returns the platform's report: every merchant, tax authority, and platform account. */
  @GetExchange
  BalanceReport platform(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);

  /** Returns the report of the merchant with this account code. */
  @GetExchange("/merchant/{merchantCode}")
  BalanceReport merchant(
      @PathVariable("merchantCode") String merchantCode,
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
