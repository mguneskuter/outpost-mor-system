package com.outpost.worker.accounting.client.ledger;

import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.api.CaptureRequest;
import com.outpost.accounting.api.PaymentApi;
import com.outpost.accounting.api.PaymentEventRequest;
import com.outpost.accounting.api.RefundRequest;
import com.outpost.worker.accounting.client.LedgerPaymentClient;
import com.outpost.worker.accounting.client.LedgerPaymentClientException;
import org.jspecify.annotations.Nullable;

/** Calls the Ledger payment routes the Worker owns through Ledger's HTTP contract. */
public final class LedgerPaymentHttpClient implements LedgerPaymentClient {
  private final PaymentApi paymentApi;

  /** Creates a Ledger payment client over the payment contract proxy. */
  public LedgerPaymentHttpClient(PaymentApi paymentApi) {
    this.paymentApi = paymentApi;
  }

  @Override
  public void appendPaymentEvent(
      String paymentReference,
      @Nullable String refundReference,
      TransactionEventType transactionEventType) {
    call(
        () ->
            paymentApi.appendPaymentEvent(
                new PaymentEventRequest(
                    paymentReference, refundReference, transactionEventType.getCode())));
  }

  @Override
  public void recordCapture(
      String paymentReference,
      String captureReference,
      boolean success,
      long amount,
      String currency) {
    call(
        () ->
            paymentApi.capture(
                new CaptureRequest(paymentReference, captureReference, success, amount, currency)));
  }

  @Override
  public void reserveRefund(
      String paymentReference,
      String refundReference,
      long netAmount,
      long taxAmount,
      String currency) {
    call(
        () ->
            paymentApi.refund(
                new RefundRequest(
                    paymentReference, refundReference, netAmount, taxAmount, currency)));
  }

  private static void call(Runnable ledgerCall) {
    try {
      ledgerCall.run();
    } catch (RuntimeException exception) {
      throw new LedgerPaymentClientException(exception);
    }
  }
}
