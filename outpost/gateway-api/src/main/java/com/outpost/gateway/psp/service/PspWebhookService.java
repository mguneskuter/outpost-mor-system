package com.outpost.gateway.psp.service;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.framework.queue.QueueFullException;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.integration.psp.simulator.repository.PspConfiguration;
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
   * Queues one PSP event notification whose signature already matched {@code psp}.
   *
   * <p>A valid signature proves which PSP sent the event, not that the event belongs to the order
   * it names, so the event is queued only when that order was created with the signing PSP's
   * account and carries the event's PSP reference.
   *
   * @return {@code ACCEPTED} when the event was queued; every other code means nothing was queued
   */
  public PspWebhookProcessResultCodes process(PspConfiguration psp, PspOrderEvent event) {
    LOGGER.info("PSP event received", eventFields(event));
    PspWebhookProcessResultCodes result = queue(psp, event);
    LOGGER.info(
        result == PspWebhookProcessResultCodes.ACCEPTED
            ? "PSP event queued for the Ledger"
            : "PSP event not queued",
        new StructuredLogField(LogField.PSP_REFERENCE, event.pspReference()),
        new StructuredLogField(LogField.ORDER_REFERENCE, event.orderReference()),
        new StructuredLogField(LogField.EVENT_CODE, event.eventCode().name()),
        new StructuredLogField(LogField.WEBHOOK_RESULT, result.name()));
    return result;
  }

  private PspWebhookProcessResultCodes queue(PspConfiguration psp, PspOrderEvent event) {
    if (!psp.code().equals(event.pspCode())) {
      return PspWebhookProcessResultCodes.INVALID_PAYLOAD;
    }
    Optional<Order> found = orders.findOrderByOrderReference(event.orderReference());
    if (found.isEmpty()) {
      return PspWebhookProcessResultCodes.UNKNOWN_PAYMENT;
    }
    Order order = found.orElseThrow();
    if (order.getPspAccount().getAccountId() != psp.accountId()) {
      return PspWebhookProcessResultCodes.FOREIGN_PAYMENT;
    }
    if (!order.getPspReference().map(event.pspReference()::equals).orElse(false)) {
      return PspWebhookProcessResultCodes.PSP_REFERENCE_MISMATCH;
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
      return PspWebhookProcessResultCodes.INVALID_PAYLOAD;
    }
    try {
      accountingQueue.add(
          new AccountingQueueRequest(
              type,
              order.getOrderReference(),
              order.getMerchantReference(),
              psp.code(),
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
      return PspWebhookProcessResultCodes.QUEUE_FULL;
    }
    return PspWebhookProcessResultCodes.ACCEPTED;
  }

  private static StructuredLogField[] eventFields(PspOrderEvent event) {
    return new StructuredLogField[] {
      new StructuredLogField(LogField.PSP_CODE, event.pspCode()),
      new StructuredLogField(LogField.PSP_REFERENCE, event.pspReference()),
      new StructuredLogField(LogField.ORDER_REFERENCE, event.orderReference()),
      new StructuredLogField(LogField.EVENT_CODE, event.eventCode().name()),
      new StructuredLogField(LogField.SUCCESS, Boolean.toString(event.success())),
      new StructuredLogField(LogField.RESULT_CODE, event.resultCode())
    };
  }

  private enum LogField implements LogFields {
    PSP_CODE("psp_code"),
    PSP_REFERENCE("psp_reference"),
    ORDER_REFERENCE("order_reference"),
    EVENT_CODE("event_code"),
    SUCCESS("success"),
    RESULT_CODE("result_code"),
    WEBHOOK_RESULT("webhook_result");

    private final String jsonKey;

    LogField(String jsonKey) {
      this.jsonKey = jsonKey;
    }

    @Override
    public String getJsonKey() {
      return jsonKey;
    }
  }
}
