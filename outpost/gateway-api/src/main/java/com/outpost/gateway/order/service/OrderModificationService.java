package com.outpost.gateway.order.service;

import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.RefundRequest;
import com.outpost.integration.psp.service.RefundResult;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.payment.refund.Refund;
import com.outpost.payment.refund.repository.RefundRepository;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Refunds a merchant's order in full at its PSP and stores the accepted refund. */
public final class OrderModificationService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(OrderModificationService.class));
  private static final String REFUND_TYPE = "REFUND";
  private final OrderRepository orders;
  private final PspClient psp;
  private final RefundRepository refunds;

  /** Creates a service over the order and refund stores and the PSP. */
  public OrderModificationService(OrderRepository orders, PspClient psp, RefundRepository refunds) {
    this.orders = orders;
    this.psp = psp;
    this.refunds = refunds;
  }

  /**
   * Refunds one order owned by the caller in full.
   *
   * @throws ModifyOrderException 400 UNSUPPORTED_MODIFICATION_TYPE, 404 ORDER_NOT_FOUND, 409
   *     ORDER_NOT_PAID, 422 REFUND_REJECTED, 503 PSP_RETRYABLE
   */
  public ModifyOrderResult request(long merchantAccountId, ModifyOrderCommand command) {
    if (!REFUND_TYPE.equals(command.type())) {
      throw failure(HttpStatus.BAD_REQUEST.value(), "UNSUPPORTED_MODIFICATION_TYPE");
    }
    Order order =
        orders
            .findOrderByOrderReference(command.orderReference())
            .filter(found -> found.getMerchantAccount().getAccountId() == merchantAccountId)
            .orElseThrow(() -> failure(HttpStatus.NOT_FOUND.value(), "ORDER_NOT_FOUND"));
    String pspReference =
        order
            .getPspReference()
            .orElseThrow(() -> failure(HttpStatus.CONFLICT.value(), "ORDER_NOT_PAID"));
    String refundReference = "refund-" + UUID.randomUUID();
    StructuredLogField[] refundFields = {
      new StructuredLogField(LogField.ORDER_REFERENCE, order.getOrderReference()),
      new StructuredLogField(LogField.REFUND_REFERENCE, refundReference),
      new StructuredLogField(LogField.PSP_CODE, order.getPspAccount().getCode()),
      new StructuredLogField(LogField.PSP_REFERENCE, pspReference)
    };
    LOGGER.info("Refund requested", refundFields);

    RefundResult pspResult;
    try {
      pspResult =
          psp.refund(
              new RefundRequest(order.getPspAccount().getCode(), pspReference, refundReference));
    } catch (RuntimeException exception) {
      LOGGER.warn("PSP refund failed", exception, refundFields);
      throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "PSP_RETRYABLE");
    }
    LOGGER.info(
        "PSP answered the refund",
        new StructuredLogField(LogField.ORDER_REFERENCE, order.getOrderReference()),
        new StructuredLogField(LogField.REFUND_REFERENCE, refundReference),
        new StructuredLogField(LogField.PSP_RESULT, pspResult.resultCode().name()));
    return switch (pspResult.resultCode()) {
      case ACCEPTED -> {
        String pspRefundReference = pspResult.pspRefundReference();
        if (pspRefundReference == null || pspRefundReference.isBlank()) {
          throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "PSP_RETRYABLE");
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
        yield new ModifyOrderResult(refundReference);
      }
      case REJECTED -> throw failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "REFUND_REJECTED");
      case UNKNOWN -> throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "PSP_RETRYABLE");
    };
  }

  private static ModifyOrderException failure(int status, String code) {
    return new ModifyOrderException(status, code);
  }

  private enum LogField implements LogFields {
    ORDER_REFERENCE("order_reference"),
    REFUND_REFERENCE("refund_reference"),
    PSP_CODE("psp_code"),
    PSP_REFERENCE("psp_reference"),
    PSP_RESULT("psp_result");

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
