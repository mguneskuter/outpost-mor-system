package com.outpost.accounting.queue;

import com.outpost.platform.staticdata.StaticData;

/** Terminal outcomes for accounting requests. */
@StaticData
public enum AccountingRequestResults {
  SUCCESS(1L),
  FAILED(2L);

  @SuppressWarnings("Immutable")
  private final AccountingRequestResult value;

  AccountingRequestResults(long id) {
    value = new AccountingRequestResult(id, name());
  }

  /** Returns this constant's persisted value. */
  public AccountingRequestResult getValue() {
    return value;
  }

  /** Persisted accounting request result. */
  public record AccountingRequestResult(long accountingRequestResultId, String code) {
    public long getAccountingRequestResultId() {
      return accountingRequestResultId;
    }

    public String getCode() {
      return code;
    }
  }
}
