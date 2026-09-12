package com.outpost.ledger.report.service;

import java.util.List;

/** One Ledger balance report with accounts grouped by their currency balances. */
public record BalanceReport(List<Account> accounts) {
  /** Copies the account list so the read model remains immutable. */
  public BalanceReport {
    accounts = List.copyOf(accounts);
  }

  /** One account and its balances without currency conversion. */
  public record Account(String accountCode, String name, List<Balance> balances) {
    /** Copies the balance list so the read model remains immutable. */
    public Account {
      balances = List.copyOf(balances);
    }
  }

  /** One account balance in the line's currency. */
  public record Balance(String currency, long amount) {}
}
