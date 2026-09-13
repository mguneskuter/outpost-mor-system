package com.outpost.gateway.psp.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.account.Account;
import java.util.List;

/** HTTP response listing the PSPs enabled for a merchant. */
public record ListPspsResponse(@JsonProperty("psps") List<Psp> psps) {
  /** Copies the PSP list so the response cannot be mutated through its input. */
  public ListPspsResponse {
    psps = List.copyOf(psps);
  }

  /** Converts the enabled PSP accounts to the HTTP contract. */
  public static ListPspsResponse from(List<Account> accounts) {
    return new ListPspsResponse(
        accounts.stream().map(account -> new Psp(account.getCode(), account.getName())).toList());
  }

  /** One enabled PSP in the HTTP response. */
  public record Psp(@JsonProperty("code") String code, @JsonProperty("name") String name) {}
}
