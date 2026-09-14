package com.outpost.integration.psp;

import org.jspecify.annotations.Nullable;

/** Result of requesting a payment service provider refund. */
public record RefundPspOrderResult(
    @Nullable String pspReference,
    @Nullable String pspRefundReference,
    PspResultCodes resultCode) {}
