package com.outpost.gateway.order.repository.mybatis;

import com.outpost.gateway.order.service.OrderPhases;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** MyBatis projection of an order attempt. */
public record OrderRow(
    long orderId,
    String orderReference,
    String merchantReference,
    long merchantAccountId,
    String merchantCode,
    long shopperId,
    String shopperCountry,
    @Nullable String shopperCountrySubdivision,
    String paymentShopperCountry,
    @Nullable String paymentShopperCountrySubdivision,
    long currencyId,
    String currency,
    long netAmount,
    long taxAmount,
    long grossAmount,
    String idempotencyKey,
    String requestFingerprint,
    String paymentReference,
    long pspAccountId,
    String pspCode,
    @Nullable String pspReference,
    @Nullable String paymentLink,
    Instant createdAt,
    OrderPhases phase) {}
