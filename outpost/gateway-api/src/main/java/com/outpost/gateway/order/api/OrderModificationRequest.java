package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.ModifyOrderCommand;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** JSON request for modifying an existing order. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderModificationRequest(
    @Nullable String orderReference,
    @Nullable String idempotencyKey,
    @Nullable String merchantReference,
    @Nullable String type) {
  /** Converts the transport payload to an application command. */
  public ModifyOrderCommand toCommand() {
    return new ModifyOrderCommand(orderReference, idempotencyKey, merchantReference, type);
  }
}
