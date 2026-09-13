package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.ModifyOrderCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * JSON request for modifying an existing order. Each constraint carries the error code the request
 * is refused with.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderModificationRequest(
    @NotBlank(message = INVALID_ORDER_REFERENCE)
        @Size(max = CreateOrderRequest.REFERENCE_MAX_LENGTH, message = INVALID_ORDER_REFERENCE)
        String orderReference,
    @NotBlank(message = INVALID_IDEMPOTENCY_KEY)
        @Size(max = CreateOrderRequest.REFERENCE_MAX_LENGTH, message = INVALID_IDEMPOTENCY_KEY)
        String idempotencyKey,
    @NotBlank(message = INVALID_MERCHANT_REFERENCE)
        @Size(max = CreateOrderRequest.REFERENCE_MAX_LENGTH, message = INVALID_MERCHANT_REFERENCE)
        String merchantReference,
    @NotBlank(message = INVALID_TYPE) String type) {
  public static final String INVALID_ORDER_REFERENCE = "INVALID_ORDER_REFERENCE";
  public static final String INVALID_IDEMPOTENCY_KEY = "INVALID_IDEMPOTENCY_KEY";
  public static final String INVALID_MERCHANT_REFERENCE = "INVALID_MERCHANT_REFERENCE";
  public static final String INVALID_TYPE = "INVALID_TYPE";

  /** Converts the validated transport payload to an application command. */
  public ModifyOrderCommand toCommand() {
    return new ModifyOrderCommand(orderReference, idempotencyKey, merchantReference, type);
  }
}
