package com.outpost.pspsimulator.order;

/** The payment outcome codes a test card selects, reported as the webhook {@code result_code}. */
public enum ResultCodes {
  APPROVED,
  ACQUIRER_REFUSED,
  SCHEME_ERROR;

  /** Returns the exact result code, which is also the serialised value. */
  public String getCode() {
    return name();
  }
}
