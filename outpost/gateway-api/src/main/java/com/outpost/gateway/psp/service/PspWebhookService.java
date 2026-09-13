package com.outpost.gateway.psp.service;

import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import com.outpost.payment.repository.PspEventRepository;
import com.outpost.payment.repository.PspEventRepository.PaymentAccounts;
import com.outpost.payment.repository.PspEventRepository.ReceivedPspEvent;
import java.util.Optional;

/** Records authenticated PSP event notifications against their payment. */
public final class PspWebhookService {
  private final PspEventRepository events;

  /** Creates a webhook processing service. */
  public PspWebhookService(PspEventRepository events) {
    this.events = events;
  }

  /**
   * Records one PSP event notification whose signature already matched {@code psp}.
   *
   * <p>A valid signature proves which PSP sent the event, not that the event belongs to the payment
   * it names, so the event is recorded only when that payment was created with the signing PSP's
   * account and carries the event's PSP reference.
   *
   * @param payload the notification body exactly as signed; it is stored unchanged
   * @return {@code ACCEPTED} also when the same event was already recorded; every other code means
   *     nothing was recorded
   */
  public PspWebhookProcessResultCodes process(
      PspConfiguration psp, PspWebhookEvent event, String payload) {
    if (!psp.code().equals(event.pspCode())) {
      return PspWebhookProcessResultCodes.INVALID_PAYLOAD;
    }
    Optional<PaymentAccounts> found = events.findPaymentAccounts(event.paymentReference());
    if (found.isEmpty()) {
      return PspWebhookProcessResultCodes.UNKNOWN_PAYMENT;
    }
    PaymentAccounts payment = found.orElseThrow();
    if (payment.pspAccountId() != psp.accountId()) {
      return PspWebhookProcessResultCodes.FOREIGN_PAYMENT;
    }
    if (!event.pspReference().equals(payment.pspReference())) {
      return PspWebhookProcessResultCodes.PSP_REFERENCE_MISMATCH;
    }
    events.recordReceived(
        new ReceivedPspEvent(
            payment.merchantAccountId(),
            payment.pspAccountId(),
            event.eventReference(),
            event.paymentReference(),
            event.eventCode(),
            payload));
    return PspWebhookProcessResultCodes.ACCEPTED;
  }
}
