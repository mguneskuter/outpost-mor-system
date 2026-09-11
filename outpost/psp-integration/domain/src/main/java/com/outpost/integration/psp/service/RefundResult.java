package com.outpost.integration.psp.service;

import org.jspecify.annotations.Nullable;

/** Result of requesting a payment service provider refund. */
public record RefundResult(
    @Nullable String pspReference, @Nullable String pspRefundReference, ResultCode resultCode) {}
