package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.OrderModificationCommand;
import com.outpost.gateway.order.service.OrderModificationCommand.RefundLineCommand;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** JSON request for modifying an existing order. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderModificationRequest(
    @Nullable String orderReference,
    @Nullable String idempotencyKey,
    @Nullable String merchantReference,
    @Nullable String type,
    @Nullable List<@Nullable RefundLine> refundLines) {
  /** Converts the transport payload to an application command. */
  public OrderModificationCommand toCommand() {
    return new OrderModificationCommand(
        orderReference,
        idempotencyKey,
        merchantReference,
        type,
        refundLines == null
            ? null
            : refundLines.stream()
                .map(
                    line ->
                        line == null
                            ? null
                            : new RefundLineCommand(
                                line.orderLineReference(),
                                line.merchantLineReference(),
                                line.amount()))
                .toList());
  }

  /** One requested refund line. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record RefundLine(
      @Nullable String orderLineReference,
      @Nullable String merchantLineReference,
      @Nullable Long amount) {}
}
