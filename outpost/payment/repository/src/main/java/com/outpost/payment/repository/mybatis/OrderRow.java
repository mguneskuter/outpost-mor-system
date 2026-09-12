package com.outpost.payment.repository.mybatis;

import java.time.Instant;

/** MyBatis projection of {@code merchant_order}. */
public record OrderRow(
    long orderId,
    String orderReference,
    String merchantReference,
    long accountId,
    long shopperId,
    long currencyId,
    long netAmount,
    long taxAmount,
    long grossAmount,
    String idempotencyKey,
    Instant createdAt) {}
