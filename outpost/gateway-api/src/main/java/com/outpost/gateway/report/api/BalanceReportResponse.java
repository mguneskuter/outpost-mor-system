package com.outpost.gateway.report.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.gateway.report.client.BalanceReport;
import java.util.List;

/** HTTP response for a balance report. */
public record BalanceReportResponse(@JsonProperty("accounts") List<Account> accounts) {
  /** Copies the account list so the response cannot be mutated through its input. */
  public BalanceReportResponse {
    accounts = List.copyOf(accounts);
  }

  /** Converts the application read model to the HTTP contract. */
  public static BalanceReportResponse from(BalanceReport report) {
    return new BalanceReportResponse(report.accounts().stream().map(Account::from).toList());
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

    private static Account from(BalanceReport.Account account) {
      return new Account(
          account.accountCode(),
          account.name(),
          account.balances().stream().map(Balance::from).toList());
    }
  }

  /** One currency balance in the HTTP response. */
  public record Balance(
      @JsonProperty("currency") String currency, @JsonProperty("amount") long amount) {
    private static Balance from(BalanceReport.Balance balance) {
      return new Balance(balance.currency(), balance.amount());
    }
  }
}
