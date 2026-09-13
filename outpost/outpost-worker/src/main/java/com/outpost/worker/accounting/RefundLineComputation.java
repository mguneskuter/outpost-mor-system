package com.outpost.worker.accounting;

import com.outpost.payment.common.Amount;
import com.outpost.payment.order.OrderItem;
import com.outpost.worker.accounting.repository.LedgerTransactionRepository.RefundedTotal;
import org.jspecify.annotations.Nullable;

/**
 * Computes one order line's refundable net and tax, following the final-residual rule: a request
 * that exhausts the line's remaining amount takes the residual tax rather than a freshly rounded
 * one, so refunding a line over one or many operations exactly reverses it.
 */
final class RefundLineComputation {
  private RefundLineComputation() {}

  /**
   * Returns the net and tax to refund for this line, or throws when the request exceeds what
   * remains refundable.
   */
  static LineRefund compute(
      long orderItemId,
      OrderItem item,
      RefundedTotal alreadyActive,
      @Nullable Long requestedAmount) {
    long remainingNet = item.getNetAmount().quantity() - alreadyActive.net();
    long remainingTax = item.getTaxAmount().quantity() - alreadyActive.tax();
    long requestedNet = requestedAmount != null ? requestedAmount : remainingNet;
    if (requestedNet <= 0 || requestedNet > remainingNet) {
      throw new RefundLineRejectedException(item.getOrderLineReference());
    }
    long requestedTax;
    if (requestedNet == remainingNet) {
      requestedTax = remainingTax;
    } else {
      requestedTax =
          new Amount(item.getNetAmount().currency(), requestedNet)
              .multipliedBy(item.getTaxRate())
              .quantity();
      if (requestedTax > remainingTax) {
        throw new RefundLineRejectedException(item.getOrderLineReference());
      }
    }
    return new LineRefund(orderItemId, requestedNet, requestedTax);
  }

  /** The net and tax to refund for one order line. */
  record LineRefund(long orderItemId, long net, long tax) {}

  /** Signals that a refund line exceeds what remains refundable. */
  static final class RefundLineRejectedException extends RuntimeException {
    RefundLineRejectedException(String orderLineReference) {
      super("Refund exceeds the refundable amount for line: " + orderLineReference);
    }
  }
}
