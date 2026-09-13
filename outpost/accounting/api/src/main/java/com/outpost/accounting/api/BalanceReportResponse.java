package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** HTTP response for a balance report. */
public record BalanceReportResponse(@JsonProperty("accounts") List<Account> accounts) {
  /** Copies the account list so the response cannot be mutated through its input. */
  public BalanceReportResponse {
    accounts = List.copyOf(accounts);
  }

  /** One account and its balances in the HTTP response. */
  public record Account(
      @JsonProperty("account_code") String accountCode,
      @JsonProperty("name") String name,
      @JsonProperty("balances") List<Balance> balances) {
    /** Copies the balance list so the response cannot be mutated through its input. */
    public Account {
      balances = List.copyOf(balances);
    }
  }

  /** One currency balance in the HTTP response. */
  public record Balance(
      @JsonProperty("currency") String currency, @JsonProperty("amount") long amount) {}
}
