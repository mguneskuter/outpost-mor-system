package com.outpost.worker.accounting.client;

import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import org.jspecify.annotations.Nullable;

/** Records Worker-observed payment outcomes in Ledger. */
public interface LedgerPaymentClient {
  /**
   * Appends a payment or refund lifecycle event in Ledger; {@code refundReference} is absent for a
   * payment event.
   */
  void appendPaymentEvent(
      String paymentReference,
      @Nullable String refundReference,
      TransactionEventType transactionEventType);

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
