package com.outpost.gateway.psp.service;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.framework.queue.QueueFullException;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.integration.psp.simulator.PspConfiguration;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.repository.OrderRepository;
import java.util.Optional;
import org.slf4j.LoggerFactory;

/** Queues authenticated PSP event notifications for the Ledger when they match their order. */
public final class PspWebhookService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(PspWebhookService.class));
  private final OrderRepository orders;
  private final TimeOrderedQueue<AccountingQueueRequest> accountingQueue;

  /** Creates a service that queues matching events on {@code accountingQueue}. */
  public PspWebhookService(
      OrderRepository orders, TimeOrderedQueue<AccountingQueueRequest> accountingQueue) {
    this.orders = orders;
    this.accountingQueue = accountingQueue;
  }

  /**
   * Queues one PSP event notification whose signature already matched {@code pspConfiguration}.
   *
   * <p>A valid signature proves which PSP sent the event, not that the event belongs to the order
   * it names, so the event is queued only when that order was created with the signing PSP's
   * account and carries the event's PSP reference.
   *
   * @return {@code ACCEPTED} when the event was queued; every other code means nothing was queued
   */
  public PspWebhookResults queueEvent(PspConfiguration pspConfiguration, PspOrderEvent event) {
    LOGGER.info("PSP event received", eventFields(event));
    PspWebhookResults result = queueMatchingEvent(pspConfiguration, event);
    LOGGER.info(
        result == PspWebhookResults.ACCEPTED
            ? "PSP event queued for the Ledger"
            : "PSP event not queued",
        new StructuredLogField(LogFields.PSP_REFERENCE, event.pspReference()),
        new StructuredLogField(LogFields.ORDER_REFERENCE, event.orderReference()),
        new StructuredLogField(LogFields.EVENT_CODE, event.eventCode().name()),
        new StructuredLogField(LogFields.WEBHOOK_RESULT, result.name()));
    return result;
  }

  private PspWebhookResults queueMatchingEvent(
      PspConfiguration pspConfiguration, PspOrderEvent event) {
    if (!pspConfiguration.code().equals(event.pspCode())) {
      return PspWebhookResults.INVALID_PAYLOAD;
    }
    Optional<Order> found = orders.findOrderByOrderReference(event.orderReference());
    if (found.isEmpty()) {
      return PspWebhookResults.UNKNOWN_ORDER;
    }
    Order order = found.orElseThrow();
    if (order.getPspAccount().getAccountId() != pspConfiguration.accountId()) {
      return PspWebhookResults.UNKNOWN_ORDER;
    }
    if (!order.getPspReference().map(event.pspReference()::equals).orElse(false)) {
      return PspWebhookResults.PSP_REFERENCE_MISMATCH;
    }
    AccountingQueueRequestTypes type =
        switch (event.eventCode()) {
          case AUTHORISATION -> AccountingQueueRequestTypes.AUTHORISATION;
          case CAPTURE -> AccountingQueueRequestTypes.CAPTURE;
          case REFUND -> AccountingQueueRequestTypes.REFUND;
        };
    String refundReference = event.refundReference();
    if (type == AccountingQueueRequestTypes.REFUND
        && (refundReference == null || refundReference.isBlank())) {
      return PspWebhookResults.INVALID_PAYLOAD;
    }
    try {
      accountingQueue.add(
          new AccountingQueueRequest(
              type,
              order.getOrderReference(),
              order.getMerchantReference(),
              pspConfiguration.code(),
              event.pspReference(),
              event.success(),
              type == AccountingQueueRequestTypes.REFUND ? refundReference : null,
              null,
              null,
              null,
              null,
              null,
              null));
    } catch (QueueFullException full) {
      return PspWebhookResults.QUEUE_FULL;
    }
    return PspWebhookResults.ACCEPTED;
  }

  private static StructuredLogField[] eventFields(PspOrderEvent event) {
    return new StructuredLogField[] {
      new StructuredLogField(LogFields.PSP_CODE, event.pspCode()),
      new StructuredLogField(LogFields.PSP_REFERENCE, event.pspReference()),
      new StructuredLogField(LogFields.ORDER_REFERENCE, event.orderReference()),
      new StructuredLogField(LogFields.EVENT_CODE, event.eventCode().name()),
      new StructuredLogField(LogFields.SUCCESS, Boolean.toString(event.success())),
      new StructuredLogField(LogFields.RESULT_CODE, event.resultCode())
    };
  }
}
