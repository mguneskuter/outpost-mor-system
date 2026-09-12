package com.outpost.pspsimulator.refund;

/**
 * The command that refunds a captured order; its constructor owns validation of untrusted input.
 *
 * @param pspReference the order's PSP reference
 * @param refundReference the caller's reference for the refund, unique per PSP
 * @param amountMinor the amount in minor units
 * @param currencyCode the ISO 4217 currency code
 */
public record RefundCommand(
    long pspReference, String refundReference, long amountMinor, String currencyCode) {

  private static final java.util.regex.Pattern CURRENCY_CODE =
      java.util.regex.Pattern.compile("[A-Z]{3}");

  /** Validates the order reference, refund reference, amount, and ISO currency code. */
  public RefundCommand {
    if (pspReference <= 0) {
      throw new IllegalArgumentException("psp reference must be positive: " + pspReference);
    }
    if (refundReference == null || refundReference.isBlank()) {
      throw new IllegalArgumentException("refund reference must not be blank");
    }
    if (amountMinor <= 0) {
      throw new IllegalArgumentException("amount must be positive: " + amountMinor);
    }
    if (currencyCode == null || !CURRENCY_CODE.matcher(currencyCode).matches()) {
      throw new IllegalArgumentException(
          "currency must be a three-letter ISO code: " + currencyCode);
    }
  }
}
