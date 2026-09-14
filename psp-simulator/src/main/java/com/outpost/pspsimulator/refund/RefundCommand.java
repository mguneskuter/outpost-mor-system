package com.outpost.pspsimulator.refund;

/**
 * The command that refunds a captured order in full; its constructor owns validation of untrusted
 * input.
 *
 * @param pspReference the order's PSP reference
 * @param refundReference the caller's reference for the refund, unique per PSP
 */
public record RefundCommand(String pspReference, String refundReference) {

  /** Validates the order reference and refund reference. */
  public RefundCommand {
    if (pspReference == null || pspReference.isBlank()) {
      throw new IllegalArgumentException("psp reference must not be blank");
    }
    if (refundReference == null || refundReference.isBlank()) {
      throw new IllegalArgumentException("refund reference must not be blank");
    }
  }
}
