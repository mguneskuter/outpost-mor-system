package com.outpost.backoffice.psp;

/** The PSP did not accept a payment submission. */
public final class PspPaymentException extends RuntimeException {
  private final String reason;

  PspPaymentException(String reason) {
    super(reason);
    this.reason = reason;
  }

  /** Why the submission was not accepted, as the shell prints it. */
  public String reason() {
    return reason;
  }
}
