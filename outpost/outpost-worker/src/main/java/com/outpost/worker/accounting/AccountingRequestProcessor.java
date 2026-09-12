package com.outpost.worker.accounting;

import com.outpost.accounting.queue.AccountingRequest;
import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.accounting.queue.AccountingRequestResults;
import com.outpost.common.iso.Currencies;
import com.outpost.worker.accounting.client.LedgerPaymentClient;
import com.outpost.worker.accounting.client.LedgerPaymentClientException;
import java.util.Arrays;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Applies each PSP outcome or merchant refund request in the accounting request queue. */
public final class AccountingRequestProcessor {
  private final AccountingRequestQueue requests;
  private final LedgerPaymentClient ledger;
  private final MerchantRefundWorkflow refundWorkflow;

  /** Creates a processor over the queue, the Ledger client, and the refund workflow. */
  public AccountingRequestProcessor(
      AccountingRequestQueue requests,
      LedgerPaymentClient ledger,
      MerchantRefundWorkflow refundWorkflow) {
    this.requests = requests;
    this.ledger = ledger;
    this.refundWorkflow = refundWorkflow;
  }

  /** Applies one claimed request when one is available. */
  public boolean processNext() {
    return processNext(AccountingRequestQueue::claimNext);
  }

  boolean processNext(AccountingRequestClaimer claimer) {
    AccountingRequest request = claimer.claimNext(requests).orElse(null);
    if (request == null) {
      return false;
    }
    requests.complete(request, apply(request));
    return true;
  }

  @FunctionalInterface
  interface AccountingRequestClaimer {
    Optional<AccountingRequest> claimNext(AccountingRequestQueue requests);
  }

  private AccountingRequestResults apply(AccountingRequest request) {
    return switch (request.getType()) {
      case AUTHORISATION_RESULT -> applyAuthorisationResult(request);
      case CAPTURE_RESULT -> applyCaptureResult(request);
      case CANCELLATION_RESULT -> applyCancellationResult(request);
      case REFUND_RESULT -> applyRefundResult(request);
      case REFUND_REQUEST -> refundWorkflow.apply(request);
    };
  }

  private AccountingRequestResults applyAuthorisationResult(AccountingRequest request) {
    String event = Boolean.TRUE.equals(request.getSuccess()) ? "AUTHORISED" : "REFUSED";
    return recordEvent(request.getOriginalReference(), null, event);
  }

  private AccountingRequestResults applyCancellationResult(AccountingRequest request) {
    if (!Boolean.TRUE.equals(request.getSuccess())) {
      return AccountingRequestResults.FAILED;
    }
    return recordEvent(request.getOriginalReference(), null, "CANCELLED");
  }

  private AccountingRequestResults applyRefundResult(AccountingRequest request) {
    String event = Boolean.TRUE.equals(request.getSuccess()) ? "REFUNDED" : "REFUND_FAILED";
    return recordEvent(request.getOriginalReference(), request.getReference(), event);
  }

  private AccountingRequestResults applyCaptureResult(AccountingRequest request) {
    try {
      ledger.recordCapture(
          request.getOriginalReference(),
          request.getReference(),
          Boolean.TRUE.equals(request.getSuccess()),
          requireLong(request.getAmount(), "amount"),
          currencyCode(requireLong(request.getCurrencyId(), "currencyId")));
      return AccountingRequestResults.SUCCESS;
    } catch (LedgerPaymentClientException exception) {
      return AccountingRequestResults.FAILED;
    }
  }

  private AccountingRequestResults recordEvent(
      String paymentReference, @Nullable String refundReference, String event) {
    try {
      ledger.recordEvent(paymentReference, refundReference, event);
      return AccountingRequestResults.SUCCESS;
    } catch (LedgerPaymentClientException exception) {
      return AccountingRequestResults.FAILED;
    }
  }

  private static long requireLong(@Nullable Long value, String name) {
    if (value == null) {
      throw new IllegalStateException(name + " is required for this accounting request");
    }
    return value;
  }

  private static String currencyCode(long currencyId) {
    return Arrays.stream(Currencies.values())
        .map(Currencies::getValue)
        .filter(currency -> currency.getCurrencyId() == currencyId)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown currency: " + currencyId))
        .getCurrencyCode();
  }
}
