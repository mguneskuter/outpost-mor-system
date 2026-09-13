package com.outpost.gateway.order.service;

/** Application input for modifying an existing order, as the request boundary validated it. */
public record ModifyOrderCommand(
    String orderReference, String idempotencyKey, String merchantReference, String type) {}
