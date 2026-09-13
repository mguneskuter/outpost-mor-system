package com.outpost.accounting.api;

/**
 * The reasons the Ledger refuses an accounting request; nothing is queued for a refused request.
 */
public enum AccountingRequestErrorTypes {
  INVALID_REQUEST("A field the request type requires is missing or blank"),
  TRANSACTION_LOCKED("Another accounting request holds the payment's transaction lock"),
  QUEUE_FULL("The Ledger's accounting queue holds its capacity");

  private final String reason;

  AccountingRequestErrorTypes(String reason) {
    this.reason = reason;
  }

  /** Returns why a request refused with this error type was refused. */
  public String getReason() {
    return reason;
  }
}
