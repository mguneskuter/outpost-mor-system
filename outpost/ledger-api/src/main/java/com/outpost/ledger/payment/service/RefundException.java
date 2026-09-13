package com.outpost.ledger.payment.service;

/** A refund that cannot be booked, with a stable code. */
public final class RefundException extends RuntimeException {
  private final int status;
  private final String code;

  /** Creates a controlled failure. */
  public RefundException(int status, String code) {
    super(code);
    this.status = status;
    this.code = code;
  }

  /** Returns the HTTP status for this failure. */
  public int status() {
    return status;
  }

  /** Returns the safe application error code. */
  public String code() {
    return code;
  }
}
