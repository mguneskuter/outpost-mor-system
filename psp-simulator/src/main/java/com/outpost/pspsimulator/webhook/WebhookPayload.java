package com.outpost.pspsimulator.webhook;

import com.outpost.pspsimulator.order.ResultCodes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * A lifecycle event reported to Outpost.
 *
 * <p>The serialised body is signed: {@code X-Outpost-Signature} carries the HMAC-SHA-256 of the
 * exact bytes, Base64-encoded. {@code amount} is in minor units, {@code currency} is the ISO 4217
 * code, and {@code refund_reference} echoes the caller's refund reference on a refund event.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WebhookPayload(
    String pspCode,
    String pspReference,
    @Nullable String pspRefundReference,
    String paymentReference,
    WebhookEventCodes eventCode,
    long timestamp,
    boolean success,
    ResultCodes resultCode,
    long amount,
    String currency,
    @Nullable String refundReference) {}
