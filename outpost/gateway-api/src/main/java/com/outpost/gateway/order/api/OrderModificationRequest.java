package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.OrderModificationCommand;
import com.outpost.gateway.order.service.OrderModificationErrorCodes;
import com.outpost.gateway.order.service.OrderModificationException;
import com.outpost.gateway.order.service.OrderModificationTypes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * JSON request for modifying an existing order. Each constraint carries the error code the request
 * is refused with.
 *
 * @param orderLineReferences the order lines to refund; absent means every line not yet refunded
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
    @NotBlank(message = INVALID_TYPE) String type,
    @Size(
            min = 1,
            max = CreateOrderRequest.ORDER_LINES_MAX_SIZE,
            message = INVALID_ORDER_LINE_REFERENCES)
        @Nullable
            List<
                @NotBlank(message = INVALID_ORDER_LINE_REFERENCE)
                @Size(
                    max = CreateOrderRequest.REFERENCE_MAX_LENGTH,
                    message = INVALID_ORDER_LINE_REFERENCE)
                String>
            orderLineReferences) {
  public static final String INVALID_ORDER_REFERENCE = "INVALID_ORDER_REFERENCE";
  public static final String INVALID_IDEMPOTENCY_KEY = "INVALID_IDEMPOTENCY_KEY";
  public static final String INVALID_MERCHANT_REFERENCE = "INVALID_MERCHANT_REFERENCE";
  public static final String INVALID_TYPE = "INVALID_TYPE";
  public static final String INVALID_ORDER_LINE_REFERENCES = "INVALID_ORDER_LINE_REFERENCES";
  public static final String INVALID_ORDER_LINE_REFERENCE = "INVALID_ORDER_LINE_REFERENCE";

  /**
   * Converts the validated transport payload to an application command.
   *
   * @throws OrderModificationException with {@code UNSUPPORTED_MODIFICATION_TYPE} when {@code type}
   *     names no modification Outpost supports
   */
  public OrderModificationCommand toCommand() {
    OrderModificationTypes modificationType =
        Arrays.stream(OrderModificationTypes.values())
            .filter(value -> value.name().equals(type))
            .findFirst()
            .orElseThrow(
                () ->
                    new OrderModificationException(
                        OrderModificationErrorCodes.UNSUPPORTED_MODIFICATION_TYPE));
    return new OrderModificationCommand(
        orderReference,
        idempotencyKey,
        merchantReference,
        modificationType,
        orderLineReferences == null ? List.of() : orderLineReferences);
  }
}
