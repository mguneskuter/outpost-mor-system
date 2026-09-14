package com.outpost.payment.repository.mybatis;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A merchant refund as {@code merchant_refund} stores it, with its order's currency code; its items
 * are read separately.
 */
record Refund(
    @Nullable Long refundId,
    String refundReference,
    long orderId,
    String originalReference,
    String merchantReference,
    String idempotencyKey,
    @Nullable String pspRefundReference,
    String currency,
    @Nullable Instant createdAt) {}
