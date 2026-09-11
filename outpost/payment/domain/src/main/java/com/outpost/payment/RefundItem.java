package com.outpost.payment;

import com.outpost.payment.common.Amount;
import java.util.Objects;

/**
 * The refunded portion of one order line. {@code refundId} identifies the ledger's {@code REFUND}
 * transaction; there is no separate refund table, because Outpost knows a refund's line composition
 * and the ledger does not.
 */
public final class RefundItem {
  private final long refundId;
  private final long orderItemId;
  private final Amount netAmount;
  private final Amount taxAmount;

  /** Creates a refund item. */
  public RefundItem(long refundId, long orderItemId, Amount netAmount, Amount taxAmount) {
    if (refundId <= 0) {
      throw new IllegalArgumentException("refundId must be positive: " + refundId);
    }
    this.refundId = refundId;
    if (orderItemId <= 0) {
      throw new IllegalArgumentException("orderItemId must be positive: " + orderItemId);
    }
    this.orderItemId = orderItemId;
    this.netAmount = Objects.requireNonNull(netAmount, "netAmount");
    if (netAmount.quantity() < 0) {
      throw new IllegalArgumentException("netAmount must not be negative: " + netAmount);
    }
    this.taxAmount = Objects.requireNonNull(taxAmount, "taxAmount");
    if (taxAmount.quantity() < 0) {
      throw new IllegalArgumentException("taxAmount must not be negative: " + taxAmount);
    }
    if (!netAmount.currency().equals(taxAmount.currency())) {
      throw new IllegalArgumentException("netAmount and taxAmount must use the same currency");
    }
  }

  /** Returns the identity of the ledger's REFUND transaction this item belongs to. */
  public long getRefundId() {
    return refundId;
  }

  /** Returns the order line this item refunds. */
  public long getOrderItemId() {
    return orderItemId;
  }

  /** Returns the refunded net amount. */
  public Amount getNetAmount() {
    return netAmount;
  }

  /** Returns the refunded tax amount. */
  public Amount getTaxAmount() {
    return taxAmount;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RefundItem that)) {
      return false;
    }
    return refundId == that.refundId
        && orderItemId == that.orderItemId
        && Objects.equals(netAmount, that.netAmount)
        && Objects.equals(taxAmount, that.taxAmount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(refundId, orderItemId, netAmount, taxAmount);
  }
}
