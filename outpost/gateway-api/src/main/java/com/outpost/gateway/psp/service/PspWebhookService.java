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
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.payment.refund.Refund;
import com.outpost.payment.refund.RefundItem;
import com.outpost.payment.refund.repository.RefundRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/** Queues authenticated PSP event notifications for the Ledger when they match their order. */
public final class PspWebhookService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(PspWebhookService.class));
  private final OrderRepository orders;
  private final RefundRepository refunds;
  private final TimeOrderedQueue<AccountingQueueRequest> accountingQueue;

  /** Creates a service that queues matching events on {@code accountingQueue}. */
  public PspWebhookService(
      OrderRepository orders,
      RefundRepository refunds,
      TimeOrderedQueue<AccountingQueueRequest> accountingQueue) {
    this.orders = orders;
    this.refunds = refunds;
    this.accountingQueue = accountingQueue;
  }

  /**
   * Queues one PSP event notification whose signature already matched {@code pspConfiguration}.
   *
   * <p>A valid signature proves which PSP sent the event, not that the event belongs to the order
   * it names, so the event is queued only when that order was created with the signing PSP's
   * account and carries the event's PSP reference. A REFUND event is queued only when it names a
   * stored refund of that order and echoes the refund's amount, currency, lines, and any PSP refund
   * reference already stored exactly.
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
    return switch (event.eventCode()) {
      case AUTHORISATION ->
          queue(order, pspConfiguration, event, AccountingQueueRequestTypes.AUTHORISATION, null);
      case CAPTURE ->
          queue(order, pspConfiguration, event, AccountingQueueRequestTypes.CAPTURE, null);
      case REFUND -> queueRefundEvent(order, pspConfiguration, event);
    };
  }

  private PspWebhookResults queueRefundEvent(
      Order order, PspConfiguration pspConfiguration, PspOrderEvent event) {
    String refundReference = event.refundReference();
    if (refundReference == null || refundReference.isBlank()) {
      return PspWebhookResults.INVALID_PAYLOAD;
    }
    Optional<Refund> found =
        refunds
            .findRefundByRefundReference(refundReference)
            .filter(refund -> refund.originalReference().equals(order.getOrderReference()));
    if (found.isEmpty()) {
      return PspWebhookResults.UNKNOWN_REFUND;
    }
    Refund refund = found.orElseThrow();
    // Require the PSP to echo the stored refund exactly — the Ledger books the stored lines'
    // amounts, so an event that names other amounts or lines is never queued.
    if (!echoes(event, refund)) {
      return PspWebhookResults.INVALID_PAYLOAD;
    }
    String pspRefundReference = event.pspRefundReference();
    if (pspRefundReference != null && !pspRefundReference.isBlank()) {
      String stored = refund.pspRefundReference();
      if (stored != null && !stored.equals(pspRefundReference)) {
        return PspWebhookResults.INVALID_PAYLOAD;
      }
      refunds.updateRefundPspRefundReference(refundReference, pspRefundReference);
    }
    if (event.success()) {
      if (refund.items().stream().anyMatch(RefundItem::isRefundFailed)) {
        return PspWebhookResults.INVALID_PAYLOAD;
      }
    } else {
      // Release the lines — a failed refund's lines may be refunded again.
      refunds.updateRefundItemRefundFailed(refundReference);
    }
    return queue(order, pspConfiguration, event, AccountingQueueRequestTypes.REFUND, refund);
  }

  private PspWebhookResults queue(
      Order order,
      PspConfiguration pspConfiguration,
      PspOrderEvent event,
      AccountingQueueRequestTypes type,
      @Nullable Refund refund) {
    try {
      accountingQueue.add(
          new AccountingQueueRequest(
              type,
              order.getOrderReference(),
              order.getMerchantReference(),
              pspConfiguration.code(),
              event.pspReference(),
              event.success(),
              refund == null ? null : refund.refundReference(),
              null,
              null,
              null,
              refund == null ? null : refund.netAmount(),
              refund == null ? null : refund.taxAmount(),
              refund == null ? null : refund.grossAmount()));
    } catch (QueueFullException full) {
      return PspWebhookResults.QUEUE_FULL;
    }
    return PspWebhookResults.ACCEPTED;
  }

  /** Whether the event carries the refund's currency, gross, and every line with its amounts. */
  private static boolean echoes(PspOrderEvent event, Refund refund) {
    if (!event.currency().equals(refund.netAmount().currency().getCurrencyCode())
        || event.amount() != refund.grossAmount().quantity()
        || event.refundLines().size() != refund.items().size()) {
      return false;
    }
    Map<String, OrderItem> unmatched = new HashMap<>();
    for (RefundItem item : refund.items()) {
      unmatched.put(item.orderItem().getOrderLineReference(), item.orderItem());
    }
    for (PspRefundLine line : event.refundLines()) {
      OrderItem item = unmatched.remove(line.orderLineReference());
      if (item == null
          || line.netAmount() != item.getNetAmount().quantity()
          || line.grossAmount() != item.getNetAmount().plus(item.getTaxAmount()).quantity()
          || line.taxRate().compareTo(item.getTaxRate()) != 0) {
        return false;
      }
    }
    return unmatched.isEmpty();
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
