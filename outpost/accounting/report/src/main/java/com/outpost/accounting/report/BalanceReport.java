package com.outpost.accounting.report;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * The balances posted during one period, grouped as account, balance account, and currency. Every
 * balance account of every account in the report is listed, with no balances when nothing was
 * posted to it in the period.
 */
public record BalanceReport(
    @JsonProperty("from") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate from,
    @JsonProperty("to") @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate to,
    @JsonProperty("accounts") List<Account> accounts) {
  /** Copies the account list so the report cannot be changed through its input. */
  public BalanceReport {
    accounts = List.copyOf(accounts);
  }

  /** One account and its balance accounts, ordered by balance account code. */
  public record Account(
      @JsonProperty("account_code") String accountCode,
      @JsonProperty("balance_accounts") List<BalanceAccount> balanceAccounts) {
    /** Copies the balance account list so the account cannot be changed through its input. */
    public Account {
      balanceAccounts = List.copyOf(balanceAccounts);
    }
  }

  /** One balance account and its balances, ordered by currency code. */
  public record BalanceAccount(
      @JsonProperty("balance_account_code") String balanceAccountCode,
      @JsonProperty("balances") List<Balance> balances) {
    /** Copies the balance list so the balance account cannot be changed through its input. */
    public BalanceAccount {
      balances = List.copyOf(balances);
    }
  }

  /**
   * One balance in major units of its currency as a plain decimal string, positive on the balance
   * account's normal side.
   */
  public record Balance(
      @JsonProperty("currency") String currency, @JsonProperty("balance") String balance) {}
}
