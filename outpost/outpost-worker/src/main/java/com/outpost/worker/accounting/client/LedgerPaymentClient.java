package com.outpost.worker.accounting.client;

import org.jspecify.annotations.Nullable;

/** Records Worker-observed payment outcomes in Ledger. */
public interface LedgerPaymentClient {
  /**
   * Records a payment or refund lifecycle event; {@code refundReference} is absent for a payment.
   */
  void recordEvent(String paymentReference, @Nullable String refundReference, String event);

  /** Records a PSP capture outcome. */
  void recordCapture(
      String paymentReference,
      String captureReference,
      boolean success,
      long amount,
      String currency);

  /** Reserves a refundable net and tax amount ahead of calling the PSP. */
  void reserveRefund(
      String paymentReference,
      String refundReference,
      long netAmount,
      long taxAmount,
      String currency);
}
