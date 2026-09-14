package com.outpost.pspsimulator.refund;

import com.outpost.pspsimulator.ConflictException;
import com.outpost.pspsimulator.NotFoundException;
import com.outpost.pspsimulator.order.Order;
import com.outpost.pspsimulator.order.OrderRepository;
import com.outpost.pspsimulator.order.OrderStatuses;
import com.outpost.pspsimulator.webhook.WebhookScheduler;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Acknowledges a refund of a captured order and reports by REFUND webhook whether it succeeded: it
 * does when the order is captured and the amount fits what its earlier successful refunds left.
 */
public final class RefundService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RefundService.class);

  private final OrderRepository orderRepository;
  private final RefundRepository refundRepository;
  private final WebhookScheduler webhookScheduler;

  /** Creates a refund service with order, refund, and webhook collaborators. */
  public RefundService(
      OrderRepository orderRepository,
      RefundRepository refundRepository,
      WebhookScheduler webhookScheduler) {
    this.orderRepository = orderRepository;
    this.refundRepository = refundRepository;
    this.webhookScheduler = webhookScheduler;
  }

  /**
   * Acknowledges a refund and schedules its REFUND webhook, or returns the existing refund when the
   * refund reference repeats.
   *
   * @throws NotFoundException when the order is unknown
   * @throws IllegalArgumentException when the payment reference or currency is not the order's
   * @throws ConflictException when the refund reference was used for another order
   */
  public RefundResult refund(String pspCode, RefundCommand command) {
    Order order =
        orderRepository
            .findByPspReference(pspCode, command.pspReference())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "no order with psp reference: " + command.pspReference()));
    if (!order.paymentReference().equals(command.paymentReference())) {
      throw new IllegalArgumentException(
          "payment reference is not the order's: " + command.paymentReference());
    }
    if (!order.currencyCode().equals(command.currencyCode())) {
      throw new IllegalArgumentException("currency is not the order's: " + command.currencyCode());
    }
    boolean succeeded =
        order.status() == OrderStatuses.CAPTURED
            && command.amountMinor()
                <= order.amountMinor()
                    - refundRepository.sumSucceededAmount(pspCode, command.pspReference());
    Optional<Refund> inserted =
        refundRepository.insert(
            pspCode,
            command.pspReference(),
            command.refundReference(),
            command.amountMinor(),
            command.currencyCode(),
            succeeded);
    if (inserted.isPresent()) {
      Refund refund = inserted.get();
      LOGGER.info(
          "refund {} pspCode={} pspReference={} pspRefundReference={} refundReference={} "
              + "amount={} orderStatus={}",
          refund.succeeded() ? "succeeded" : "failed",
          pspCode,
          order.pspReference(),
          refund.pspRefundReference(),
          command.refundReference(),
          refund.amountMinor(),
          order.status().getCode());
      webhookScheduler.scheduleRefund(order, refund, command.refundLines());
      return new RefundResult(refund.pspRefundReference(), true);
    }
    Refund existing =
        refundRepository
            .findByRefundReference(pspCode, command.refundReference())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "refund reference claimed but not persisted: "
                            + command.refundReference()));
    if (!existing.pspReference().equals(command.pspReference())) {
      throw new ConflictException(
          "refund reference " + command.refundReference() + " was used for another order");
    }
    return new RefundResult(existing.pspRefundReference(), true);
  }

  /** The result of a refund command: the PSP refund reference and whether it was acknowledged. */
  public record RefundResult(String pspRefundReference, boolean accepted) {}
}
