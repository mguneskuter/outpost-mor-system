package com.outpost.payment.refund;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A full refund of an order that the PSP accepted.
 *
 * @param refundId absent until the refund is stored
 * @param originalReference the refunded order's reference
 * @param pspRefundReference the PSP's reference for the refund
 * @param createdAt absent until the refund is stored
 */
public record Refund(
    @Nullable Long refundId,
    String refundReference,
    long orderId,
    String originalReference,
    String merchantReference,
    String idempotencyKey,
    String pspRefundReference,
    @Nullable Instant createdAt) {
  /**
   * Rejects a blank text component, a non-positive order id, and a non-positive refund id.
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
    requireText(pspRefundReference, "pspRefundReference");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be null or blank");
    }
  }
}
