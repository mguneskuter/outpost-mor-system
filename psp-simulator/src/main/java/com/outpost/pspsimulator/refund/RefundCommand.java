package com.outpost.pspsimulator.refund;

/**
 * The command that refunds a captured order in full; its constructor owns validation of untrusted
 * input.
 *
 * @param pspReference the order's PSP reference
 * @param refundReference the caller's reference for the refund, unique per PSP
 */
public record RefundCommand(long pspReference, String refundReference) {

  /** Validates the order reference and refund reference. */
  public RefundCommand {
    if (pspReference <= 0) {
      throw new IllegalArgumentException("psp reference must be positive: " + pspReference);
    }
    if (refundReference == null || refundReference.isBlank()) {
      throw new IllegalArgumentException("refund reference must not be blank");
    }
  }
}
