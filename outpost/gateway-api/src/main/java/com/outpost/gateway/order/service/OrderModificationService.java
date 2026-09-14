package com.outpost.gateway.order.service;

import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.integration.psp.PspClient;
import com.outpost.integration.psp.RefundPspOrderLine;
import com.outpost.integration.psp.RefundPspOrderRequest;
import com.outpost.integration.psp.RefundPspOrderResult;
import com.outpost.integration.psp.UnknownPspResultException;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.payment.refund.Refund;
import com.outpost.payment.refund.RefundItem;
import com.outpost.payment.refund.repository.RefundRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;

/**
 * Refunds whole lines of a merchant's paid order at its PSP: the lines the merchant names, or every
 * line not yet refunded. The lines are claimed in the store before the PSP is asked, so no line is
 * refunded twice.
 */
public final class OrderModificationService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(OrderModificationService.class));
  private final OrderRepository orders;
  private final PspClient pspClient;
  private final RefundRepository refunds;

  /** Creates a service over the order and refund stores and the PSP. */
  public OrderModificationService(
      OrderRepository orders, PspClient pspClient, RefundRepository refunds) {
    this.orders = orders;
    this.pspClient = pspClient;
    this.refunds = refunds;
  }

  /**
   * Applies the requested modification to one order owned by the caller.
   *
   * @throws OrderModificationException for an order the caller does not own or that has no payment,
   *     a named line that is not the order's, repeated, or already refunded, an order with no line
   *     left to refund, a refund the PSP refused, or a PSP result that is unknown
   */
  public OrderModificationResult modify(long merchantAccountId, OrderModificationCommand command) {
    return switch (command.type()) {
      case REFUND -> refund(merchantAccountId, command);
    };
  }

  private OrderModificationResult refund(long merchantAccountId, OrderModificationCommand command) {
    Order order =
        orders
            .findOrderByOrderReference(command.orderReference())
            .filter(found -> found.getMerchantAccount().getAccountId() == merchantAccountId)
            .orElseThrow(() -> failure(OrderModificationErrorCodes.ORDER_NOT_FOUND));
    String pspReference =
        order
            .getPspReference()
            .orElseThrow(() -> failure(OrderModificationErrorCodes.ORDER_NOT_PAID));
    List<OrderItem> lines = linesToRefund(order, command.orderLineReferences());
    String refundReference = "refund-" + UUID.randomUUID();
    StructuredLogField[] refundFields = {
      new StructuredLogField(LogFields.ORDER_REFERENCE, order.getOrderReference()),
      new StructuredLogField(LogFields.REFUND_REFERENCE, refundReference),
      new StructuredLogField(LogFields.PSP_CODE, order.getPspAccount().getCode()),
      new StructuredLogField(LogFields.PSP_REFERENCE, pspReference)
    };
    LOGGER.info("Refund requested", refundFields);

    // Claim the lines before the PSP call — a line with a live claim is refused here and never
    // reaches the PSP, even when two requests for it arrive together.
    Refund refund =
        refunds
            .insertRefund(
                new Refund(
                    null,
                    refundReference,
                    order.getOrderId().orElseThrow(),
                    order.getOrderReference(),
                    command.merchantReference(),
                    command.idempotencyKey(),
                    null,
                    lines.stream().map(line -> new RefundItem(null, line, false)).toList(),
                    null))
            .orElseThrow(() -> failure(OrderModificationErrorCodes.ORDER_LINE_ALREADY_REFUNDED));

    RefundPspOrderResult pspResult;
    try {
      pspResult =
          pspClient.refund(
              new RefundPspOrderRequest(
                  order.getPspAccount().getCode(),
                  pspReference,
                  order.getOrderReference(),
                  refundReference,
                  refund.grossAmount(),
                  lines.stream().map(OrderModificationService::toPspLine).toList()));
    } catch (UnknownPspResultException exception) {
      LOGGER.warn("PSP refund failed", exception, refundFields);
      throw failure(OrderModificationErrorCodes.PSP_RETRYABLE);
    }
    LOGGER.info(
        "PSP answered the refund",
        new StructuredLogField(LogFields.ORDER_REFERENCE, order.getOrderReference()),
        new StructuredLogField(LogFields.REFUND_REFERENCE, refundReference),
        new StructuredLogField(LogFields.PSP_RESULT, pspResult.resultCode().name()));
    return switch (pspResult.resultCode()) {
      case ACCEPTED -> {
        String pspRefundReference = pspResult.pspRefundReference();
        if (pspRefundReference == null || pspRefundReference.isBlank()) {
          throw failure(OrderModificationErrorCodes.PSP_RETRYABLE);
        }
        refunds.updateRefundPspRefundReference(refundReference, pspRefundReference);
        yield new OrderModificationResult(refundReference);
      }
      case REJECTED -> {
        refunds.updateRefundItemRefundFailed(refundReference);
        throw failure(OrderModificationErrorCodes.REFUND_REJECTED);
      }
    };
  }

  /**
   * The lines a refund covers: the named ones, each a line of the order that no live refund claims,
   * or every unclaimed line when none is named.
   */
  private List<OrderItem> linesToRefund(Order order, List<String> orderLineReferences) {
    Set<String> claimed = new HashSet<>();
    for (Refund refund : refunds.findRefundsByOriginalReference(order.getOrderReference())) {
      for (RefundItem item : refund.items()) {
        if (!item.isRefundFailed()) {
          claimed.add(item.orderItem().getOrderLineReference());
        }
      }
    }
    if (orderLineReferences.isEmpty()) {
      List<OrderItem> unclaimed =
          order.getItems().stream()
              .filter(item -> !claimed.contains(item.getOrderLineReference()))
              .toList();
      if (unclaimed.isEmpty()) {
        throw failure(OrderModificationErrorCodes.ORDER_ALREADY_REFUNDED);
      }
      return unclaimed;
    }
    if (new HashSet<>(orderLineReferences).size() != orderLineReferences.size()) {
      throw failure(OrderModificationErrorCodes.DUPLICATE_ORDER_LINE_REFERENCE);
    }
    List<OrderItem> named = new ArrayList<>();
    for (String reference : orderLineReferences) {
      named.add(
          order.getItems().stream()
              .filter(item -> item.getOrderLineReference().equals(reference))
              .findFirst()
              .orElseThrow(() -> failure(OrderModificationErrorCodes.ORDER_LINE_NOT_FOUND)));
    }
    for (OrderItem line : named) {
      if (claimed.contains(line.getOrderLineReference())) {
        throw failure(OrderModificationErrorCodes.ORDER_LINE_ALREADY_REFUNDED);
      }
    }
    return named;
  }

  private static RefundPspOrderLine toPspLine(OrderItem line) {
    return new RefundPspOrderLine(
        line.getOrderLineReference(),
        line.getTaxRate(),
        line.getNetAmount(),
        line.getNetAmount().plus(line.getTaxAmount()));
  }

  private static OrderModificationException failure(OrderModificationErrorCodes code) {
    return new OrderModificationException(code);
  }
}
