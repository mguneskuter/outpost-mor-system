package com.outpost.merchant.cli.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A balance report: accounts and what Outpost owes each, per currency. */
public record BalanceReport(@JsonProperty("accounts") List<Account> accounts) {
  /** One account and its balances. */
  public record Account(
      @JsonProperty("account_code") String accountCode,
      @JsonProperty("name") String name,
      @JsonProperty("balances") List<Balance> balances) {}

  /** One balance in minor units of its currency. */
  public record Balance(
      @JsonProperty("currency") String currency, @JsonProperty("amount") long amount) {}
}
