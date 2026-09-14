package com.outpost.pspsimulator.order;

/**
 * The command that submits a card number against an order; its constructor owns validation of
 * untrusted input.
 */
public record PayCommand(String pspReference, String cardNumber) {

  /** Validates the PSP reference and card number submitted for payment. */
  public PayCommand {
    if (pspReference == null || pspReference.isBlank()) {
      throw new IllegalArgumentException("psp reference must not be blank");
    }
    if (cardNumber == null || cardNumber.isBlank()) {
      throw new IllegalArgumentException("card number must not be blank");
    }
  }
}
