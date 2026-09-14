package com.outpost.gateway.order.service;

/** Application input for modifying an existing order, as the request boundary validated it. */
public record OrderModificationCommand(
    String orderReference,
    String idempotencyKey,
    String merchantReference,
    OrderModificationTypes type) {}
