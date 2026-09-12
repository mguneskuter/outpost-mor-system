package com.outpost.pspsimulator.order;

/**
 * The command that creates an order; its constructor owns validation of untrusted input.
 *
 * @param paymentReference the caller's reference for the order, unique per PSP
 * @param amountMinor the amount in minor units
 * @param currencyCode the ISO 4217 currency code
 */
public record CreateOrderCommand(String paymentReference, long amountMinor, String currencyCode) {

  private static final java.util.regex.Pattern CURRENCY_CODE =
      java.util.regex.Pattern.compile("[A-Z]{3}");

  /** Validates the payment reference, positive amount, and ISO currency code. */
  public CreateOrderCommand {
    if (paymentReference == null || paymentReference.isBlank()) {
      throw new IllegalArgumentException("payment reference must not be blank");
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
