package com.outpost.integration.psp.service;

import org.jspecify.annotations.Nullable;

/** Result of cancelling a payment service provider order. */
public record CancelResult(@Nullable String pspReference, ResultCode resultCode) {}
