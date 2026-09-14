package com.outpost.gateway.order.service;

import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.integration.psp.PspClient;
import com.outpost.integration.psp.RefundPspOrderRequest;
import com.outpost.integration.psp.RefundPspOrderResult;
import com.outpost.integration.psp.UnknownPspResultException;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.payment.refund.Refund;
import com.outpost.payment.refund.repository.RefundRepository;
import java.util.UUID;
import org.slf4j.LoggerFactory;

/** Refunds a merchant's order in full at its PSP and stores the accepted refund. */
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
   *     a refund the PSP rejected, or a PSP result that is unknown
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
    String refundReference = "refund-" + UUID.randomUUID();
    StructuredLogField[] refundFields = {
      new StructuredLogField(LogFields.ORDER_REFERENCE, order.getOrderReference()),
      new StructuredLogField(LogFields.REFUND_REFERENCE, refundReference),
      new StructuredLogField(LogFields.PSP_CODE, order.getPspAccount().getCode()),
      new StructuredLogField(LogFields.PSP_REFERENCE, pspReference)
    };
    LOGGER.info("Refund requested", refundFields);

    RefundPspOrderResult pspResult;
    try {
      pspResult =
          pspClient.refund(
              new RefundPspOrderRequest(
                  order.getPspAccount().getCode(), pspReference, refundReference));
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
        refunds.insertRefund(
            new Refund(
                null,
                refundReference,
                order.getOrderId().orElseThrow(),
                order.getOrderReference(),
                command.merchantReference(),
                command.idempotencyKey(),
                pspRefundReference,
                null));
        yield new OrderModificationResult(refundReference);
      }
      case REJECTED -> throw failure(OrderModificationErrorCodes.REFUND_REJECTED);
    };
  }

  private static OrderModificationException failure(OrderModificationErrorCodes code) {
    return new OrderModificationException(code);
  }
}
