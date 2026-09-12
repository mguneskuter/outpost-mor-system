package com.outpost.payment.repository;

import com.outpost.payment.PspEventCodes;
import java.util.Optional;

/** Stores verified PSP events and resolves the payment account they concern. */
public interface PspEventQueue {
  /** Finds the merchant and PSP accounts for a payment reference. */
  Optional<PaymentAccounts> findPaymentAccounts(String paymentReference);

  /** Records a verified event for later processing. */
  void recordReceived(ReceivedPspEvent event);

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
}
