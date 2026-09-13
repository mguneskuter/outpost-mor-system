package com.outpost.accounting.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Ledger's balance report routes. */
@HttpExchange("/v1/report/balance")
public interface BalanceReportApi {
  /** Returns balances held for tax authorities. */
  @GetExchange("/tax")
  BalanceReportResponse tax();

  /** Returns balances owed to every merchant. */
  @GetExchange("/merchant")
  BalanceReportResponse merchant();

  /** Returns balances owed to the merchant with this account code. */
  @GetExchange("/merchant/{merchantCode}")
  BalanceReportResponse merchant(@PathVariable("merchantCode") String merchantCode);
}
