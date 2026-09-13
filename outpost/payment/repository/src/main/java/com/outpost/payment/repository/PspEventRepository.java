package com.outpost.payment.repository;

import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Stores verified PSP events and resolves the payment account they concern. */
public interface PspEventRepository {
  /** Finds the merchant and PSP accounts, and the stored PSP reference, for a payment reference. */
  Optional<PaymentAccounts> findPaymentAccounts(String paymentReference);

  /** Records a verified event for later processing. */
  void recordReceived(ReceivedPspEvent event);

  /** Claims the oldest event whose payment has no unfinished earlier event. */
  Optional<PspEvent> claimNext();

  /**
   * Records the terminal processing result for a claimed event, completed at the time of the
   * database transaction that records it.
   */
  void complete(long queueId, PspEventResults result);

  /**
   * Accounts associated with a payment.
   *
   * @param pspReference the PSP's reference for the payment, stored when the PSP created its order;
   *     {@code null} when no PSP order was stored for the payment
   */
  record PaymentAccounts(
      long merchantAccountId, long pspAccountId, @Nullable String pspReference) {}

  /** Verified PSP event awaiting processing. */
  record ReceivedPspEvent(
      long merchantAccountId,
      long pspAccountId,
      String reference,
      String originalReference,
      PspEventCodes eventCode,
      String payload) {}

  /** PSP event claimed for processing. */
  record PspEvent(
      long queueId,
      long merchantAccountId,
      String reference,
      String originalReference,
      PspEventCodes eventCode,
      String payload) {}
}
