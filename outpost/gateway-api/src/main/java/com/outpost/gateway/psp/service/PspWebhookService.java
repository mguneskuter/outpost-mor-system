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
   * @param payload the notification body exactly as signed; it is stored unchanged
   * @return {@code ACCEPTED} also when the same event was already recorded
   */
  public PspWebhookProcessResultCodes process(
      PspConfiguration psp, PspWebhookEvent event, String payload) {
    if (!psp.code().equals(event.pspCode())) {
      return PspWebhookProcessResultCodes.INVALID_PAYLOAD;
    }
    Optional<PaymentAccounts> accounts =
        events
            .findPaymentAccounts(event.paymentReference())
            .filter(value -> value.pspAccountId() == psp.accountId());
    if (accounts.isEmpty()) {
      return PspWebhookProcessResultCodes.UNKNOWN_PAYMENT;
    }
    PaymentAccounts paymentAccounts = accounts.orElseThrow();
    events.recordReceived(
        new ReceivedPspEvent(
            paymentAccounts.merchantAccountId(),
            paymentAccounts.pspAccountId(),
            event.eventReference(),
            event.paymentReference(),
            event.eventCode(),
            payload));
    return PspWebhookProcessResultCodes.ACCEPTED;
  }
}
