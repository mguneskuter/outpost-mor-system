package com.outpost.payment.refund;

import com.outpost.payment.order.OrderItem;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One whole order line of a refund.
 *
 * @param refundItemId absent until the item is stored
 * @param orderItem the stored order line the refund covers
 * @param isRefundFailed whether the PSP refused or failed the refund, which releases the line for
 *     another refund
 */
public record RefundItem(@Nullable Long refundItemId, OrderItem orderItem, boolean isRefundFailed) {
  /**
   * Rejects a non-positive refund item id and an order line that has not been stored.
   *
   * @throws IllegalArgumentException for either
   */
  public RefundItem {
    if (refundItemId != null && refundItemId <= 0) {
      throw new IllegalArgumentException("refundItemId must be positive: " + refundItemId);
    }
    Objects.requireNonNull(orderItem, "orderItem");
    if (orderItem.getOrderItemId().isEmpty()) {
      throw new IllegalArgumentException("orderItem must be stored before it is refunded");
    }
  }
}
