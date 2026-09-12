package com.outpost.gateway.order.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Application input for modifying an existing order. */
public record OrderModificationCommand(
    @Nullable String orderReference,
    @Nullable String idempotencyKey,
    @Nullable String merchantReference,
    @Nullable String type,
    @Nullable List<@Nullable RefundLineCommand> refundLines) {
  /** One requested refund line, naming exactly one of the order's line references. */
  public record RefundLineCommand(
      @Nullable String orderLineReference,
      @Nullable String merchantLineReference,
      @Nullable Long amount) {}
}
