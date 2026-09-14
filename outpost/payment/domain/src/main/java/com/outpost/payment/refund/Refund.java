package com.outpost.payment.refund;

import com.outpost.common.iso.Currencies.Currency;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A merchant's refund of whole order lines, stored before the PSP is asked to refund them.
 *
 * @param refundId absent until the refund is stored
 * @param originalReference the refunded order's reference
 * @param pspRefundReference the PSP's reference for the refund; absent until the PSP has
 *     acknowledged it
 * @param items the refunded lines, at least one, all in one currency
 * @param createdAt absent until the refund is stored
 */
public record Refund(
    @Nullable Long refundId,
    String refundReference,
    long orderId,
    String originalReference,
    String merchantReference,
    String idempotencyKey,
    @Nullable String pspRefundReference,
    List<RefundItem> items,
    @Nullable Instant createdAt) {
  /**
   * Rejects a blank text component, a non-positive order id, a non-positive refund id, an empty
   * item list, and items of different currencies.
   *
   * @throws IllegalArgumentException for any of those
   */
  public Refund {
    if (refundId != null && refundId <= 0) {
      throw new IllegalArgumentException("refundId must be positive: " + refundId);
    }
    requireText(refundReference, "refundReference");
    if (orderId <= 0) {
      throw new IllegalArgumentException("orderId must be positive: " + orderId);
    }
    requireText(originalReference, "originalReference");
    requireText(merchantReference, "merchantReference");
    requireText(idempotencyKey, "idempotencyKey");
    if (pspRefundReference != null) {
      requireText(pspRefundReference, "pspRefundReference");
    }
    Objects.requireNonNull(items, "items");
    if (items.isEmpty()) {
      throw new IllegalArgumentException("A refund must cover at least one order line");
    }
    Currency currency = items.getFirst().orderItem().getNetAmount().currency();
    for (RefundItem item : items) {
      if (!item.orderItem().getNetAmount().currency().equals(currency)) {
        throw new IllegalArgumentException("Every refunded line must use one currency");
      }
    }
    items = List.copyOf(items);
  }

  /** Returns the sum of the refunded lines' net amounts. */
  public Amount netAmount() {
    Amount net = zero();
    for (RefundItem item : items) {
      net = net.plus(item.orderItem().getNetAmount());
    }
    return net;
  }

  /** Returns the sum of the refunded lines' tax amounts. */
  public Amount taxAmount() {
    Amount tax = zero();
    for (RefundItem item : items) {
      tax = tax.plus(item.orderItem().getTaxAmount());
    }
    return tax;
  }

  /** Returns the net amount plus the tax amount. */
  public Amount grossAmount() {
    return netAmount().plus(taxAmount());
  }

  private Amount zero() {
    return new Amount(items.getFirst().orderItem().getNetAmount().currency(), 0L);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be null or blank");
    }
  }
}
