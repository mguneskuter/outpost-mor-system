package com.outpost.accounting.queue;

import com.outpost.platform.staticdata.StaticData;

/** Lifecycle statuses for accounting requests. */
@StaticData
public enum AccountingRequestStatuses {
  RECEIVED(1L),
  IN_PROGRESS(2L),
  DONE(3L);

  @SuppressWarnings("Immutable")
  private final AccountingRequestStatus value;

  AccountingRequestStatuses(long id) {
    value = new AccountingRequestStatus(id, name());
  }

  /** Returns this constant's persisted value. */
  public AccountingRequestStatus getValue() {
    return value;
  }

  /** Persisted accounting request status. */
  public record AccountingRequestStatus(long accountingRequestStatusId, String code) {
    public long getAccountingRequestStatusId() {
      return accountingRequestStatusId;
    }

    public String getCode() {
      return code;
    }
  }
}
