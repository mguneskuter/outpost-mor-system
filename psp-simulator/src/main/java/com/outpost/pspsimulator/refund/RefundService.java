package com.outpost.pspsimulator.refund;

import com.outpost.pspsimulator.ConflictException;
import com.outpost.pspsimulator.NotFoundException;
import com.outpost.pspsimulator.order.Order;
import com.outpost.pspsimulator.order.OrderRepository;
import com.outpost.pspsimulator.order.OrderStatuses;
import com.outpost.pspsimulator.webhook.WebhookScheduler;
import java.util.Optional;

/** Refunds a captured order and reports the outcome by REFUND webhook. */
public final class RefundService {

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
   * Refunds a captured order, or returns the existing refund when the refund reference repeats.
   *
   * @throws NotFoundException when the order is unknown
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
    boolean accepted = order.status() == OrderStatuses.CAPTURED;
    Optional<Refund> inserted =
        refundRepository.insert(
            pspCode,
            command.pspReference(),
            command.refundReference(),
            order.amountMinor(),
            order.currencyCode(),
            accepted);
    if (inserted.isPresent()) {
      Refund refund = inserted.get();
      if (refund.accepted()) {
        webhookScheduler.scheduleRefund(order, refund);
      }
      return new RefundResult(refund.pspRefundReference(), refund.accepted());
    }
    Refund existing =
        refundRepository
            .findByRefundReference(pspCode, command.refundReference())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "refund reference claimed but not persisted: "
                            + command.refundReference()));
    if (existing.pspReference() != command.pspReference()) {
      throw new ConflictException(
          "refund reference " + command.refundReference() + " was used for another order");
    }
    return new RefundResult(existing.pspRefundReference(), existing.accepted());
  }

  /** The result of a refund command: the PSP refund reference and whether it was accepted. */
  public record RefundResult(long pspRefundReference, boolean accepted) {}
}
