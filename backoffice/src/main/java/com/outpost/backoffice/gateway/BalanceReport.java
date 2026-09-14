package com.outpost.backoffice.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * A balance report: the accounts the caller may see, each with its balance accounts and the balance
 * posted to each during the period, per currency.
 */
public record BalanceReport(
    @JsonProperty("from") LocalDate from,
    @JsonProperty("to") LocalDate to,
    @JsonProperty("accounts") List<Account> accounts) {
  /** One account and its balance accounts. */
  public record Account(
      @JsonProperty("account_code") String accountCode,
      @JsonProperty("balance_accounts") List<BalanceAccount> balanceAccounts) {}

  /** One balance account and its balances; none when nothing was posted in the period. */
  public record BalanceAccount(
      @JsonProperty("balance_account_code") String balanceAccountCode,
      @JsonProperty("balances") List<Balance> balances) {}

  /** One balance in major units of its currency, positive on the balance account's normal side. */
  public record Balance(
      @JsonProperty("currency") String currency, @JsonProperty("balance") String balance) {}
}
