package com.outpost.gateway.psp.service;

import org.jspecify.annotations.Nullable;

/**
 * A PSP event notification about one order.
 *
 * @param orderReference the original reference the PSP echoes as {@code payment_reference}
 * @param pspReference the PSP's reference for the order the event concerns
 * @param refundReference Outpost's refund reference; present on a REFUND event
 */
public record PspWebhookEvent(
    String pspCode,
    String pspReference,
    String orderReference,
    PspWebhookEventCodes eventCode,
    boolean success,
    @Nullable String refundReference) {}
