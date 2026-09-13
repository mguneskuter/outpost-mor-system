package com.outpost.payment.repository;

import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import java.time.Instant;
import java.util.Optional;

/** Stores verified PSP events and resolves the payment account they concern. */
public interface PspEventRepository {
  /** Finds the merchant and PSP accounts for a payment reference. */
  Optional<PaymentAccounts> findPaymentAccounts(String paymentReference);

  /** Records a verified event for later processing. */
  void recordReceived(ReceivedPspEvent event);

  /** Claims the oldest event whose payment has no unfinished earlier event. */
  Optional<PspEvent> claimNext();

  /** Records the terminal processing result for a claimed event. */
  void complete(long queueId, PspEventResults result, Instant completedAt);

  /** Accounts associated with a payment. */
  record PaymentAccounts(long merchantAccountId, long pspAccountId) {}

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
