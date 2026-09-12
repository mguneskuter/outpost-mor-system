package com.outpost.accounting.queue;

import com.outpost.platform.staticdata.StaticData;

/** The kinds of work accepted by the accounting request queue. */
@StaticData
public enum AccountingRequestTypes {
  AUTHORISATION_RESULT(1L),
  CAPTURE_RESULT(2L),
  CANCELLATION_RESULT(3L),
  REFUND_RESULT(4L),
  REFUND_REQUEST(5L);

  @SuppressWarnings("Immutable")
  private final AccountingRequestType value;

  AccountingRequestTypes(long id) {
    value = new AccountingRequestType(id, name());
  }

  /** Returns this constant's persisted value. */
  public AccountingRequestType getValue() {
    return value;
  }

  /** Persisted accounting request type. */
  public record AccountingRequestType(long accountingRequestTypeId, String code) {
    public long getAccountingRequestTypeId() {
      return accountingRequestTypeId;
    }

    public String getCode() {
      return code;
    }
  }
}
