package com.outpost.ledger.payment.service;

/** Controlled failure that is safe to expose when reserving a refund. */
public final class RefundReservationException extends RuntimeException {
  private final int status;
  private final String code;

  /** Creates a controlled failure. */
  public RefundReservationException(int status, String code) {
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
