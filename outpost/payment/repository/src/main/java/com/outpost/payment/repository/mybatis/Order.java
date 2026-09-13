package com.outpost.payment.repository.mybatis;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A payment order as {@code merchant_order} stores it: jurisdiction and currency as codes, amounts
 * as minor units.
 */
record Order(
    @Nullable Long orderId,
    String orderReference,
    String merchantReference,
    long accountId,
    long shopperId,
    String shopperCountry,
    @Nullable String shopperCountrySubdivision,
    String currency,
    long netAmount,
    long taxAmount,
    long grossAmount,
    String idempotencyKey,
    String requestFingerprint,
    long pspAccountId,
    @Nullable String pspReference,
    @Nullable String paymentLink,
    @Nullable Instant createdAt) {}
