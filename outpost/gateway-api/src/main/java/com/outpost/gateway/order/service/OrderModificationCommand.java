package com.outpost.gateway.order.service;

import java.util.List;
import java.util.Objects;

/**
 * Application input for modifying an existing order, as the request boundary validated it.
 *
 * @param orderLineReferences the order lines to refund; empty means every line not yet refunded
 */
public record OrderModificationCommand(
    String orderReference,
    String idempotencyKey,
    String merchantReference,
    OrderModificationTypes type,
    List<String> orderLineReferences) {
  /** Copies {@code orderLineReferences}. */
  public OrderModificationCommand {
    orderLineReferences = List.copyOf(Objects.requireNonNull(orderLineReferences));
  }
}
