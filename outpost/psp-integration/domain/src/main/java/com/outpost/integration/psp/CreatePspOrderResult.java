package com.outpost.integration.psp;

import org.jspecify.annotations.Nullable;

/** Result of creating an order at a payment service provider. */
public record CreatePspOrderResult(
    @Nullable String pspReference, @Nullable String paymentUrl, PspResultCodes resultCode) {}
