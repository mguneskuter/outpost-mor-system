package com.outpost.pspsimulator.refund;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The command that refunds part or all of a captured order; its constructor owns validation of
 * untrusted input.
 *
 * @param pspReference the order's PSP reference
 * @param paymentReference the caller's reference for the order
 * @param refundReference the caller's reference for the refund, unique per PSP
 * @param amountMinor the amount to refund in minor units, the sum of the lines' gross amounts
 * @param currencyCode the ISO 4217 currency code
 * @param refundLines the refunded lines, at least one
 */
public record RefundCommand(
    String pspReference,
    String paymentReference,
    String refundReference,
    long amountMinor,
    String currencyCode,
    List<RefundLine> refundLines) {

  private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

  /**
   * Validates the references, the positive amount, the ISO currency code, and the lines: each has a
   * reference, a positive net, and a gross of at least its net, and their gross amounts sum to the
   * amount.
   */
  public RefundCommand {
    requireText(pspReference, "psp reference");
    requireText(paymentReference, "payment reference");
    requireText(refundReference, "refund reference");
    if (amountMinor <= 0) {
      throw new IllegalArgumentException("amount must be positive: " + amountMinor);
    }
    if (currencyCode == null || !CURRENCY_CODE.matcher(currencyCode).matches()) {
      throw new IllegalArgumentException(
          "currency must be a three-letter ISO code: " + currencyCode);
    }
    if (refundLines == null || refundLines.isEmpty()) {
      throw new IllegalArgumentException("refund lines must name at least one line");
    }
    long grossSum = 0;
    for (RefundLine line : refundLines) {
      requireText(line.orderLineReference(), "order line reference");
      if (line.netAmount() <= 0) {
        throw new IllegalArgumentException("line net must be positive: " + line.netAmount());
      }
      if (line.grossAmount() < line.netAmount()) {
        throw new IllegalArgumentException(
            "line gross must not be below its net: " + line.grossAmount());
      }
      grossSum = Math.addExact(grossSum, line.grossAmount());
    }
    if (grossSum != amountMinor) {
      throw new IllegalArgumentException(
          "lines' gross amounts must sum to the amount: " + grossSum + " vs " + amountMinor);
    }
    refundLines = List.copyOf(refundLines);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
