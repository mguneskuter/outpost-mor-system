package com.outpost.integration.psp.service;

import org.jspecify.annotations.Nullable;

/** Result of creating an order at a payment service provider. */
public record CreateOrderResult(
    @Nullable String pspReference, @Nullable String paymentUrl, ResultCode resultCode) {}
