package com.outpost.gateway.order.service;

import org.jspecify.annotations.Nullable;

/** Application input for modifying an existing order. */
public record ModifyOrderCommand(
    @Nullable String orderReference,
    @Nullable String idempotencyKey,
    @Nullable String merchantReference,
    @Nullable String type) {}
