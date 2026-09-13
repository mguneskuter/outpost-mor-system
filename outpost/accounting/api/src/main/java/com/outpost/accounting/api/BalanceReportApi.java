package com.outpost.accounting.api;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Ledger's balance report routes. */
@HttpExchange("/v1/report/balance")
public interface BalanceReportApi {
  /** Returns balances held for tax authorities. */
  @GetExchange("/tax")
  BalanceReportResponse tax();

  /** Returns balances owed to merchants. */
  @GetExchange("/merchant")
  BalanceReportResponse merchant();
}
