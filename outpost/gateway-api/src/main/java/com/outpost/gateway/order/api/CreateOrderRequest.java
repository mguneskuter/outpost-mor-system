package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.CreateOrderCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderLineCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.ShopperDetailsCommand;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** JSON request for creating a payment order. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateOrderRequest(
    String merchantReference,
    String idempotencyKey,
    ShopperDetails shopperDetails,
    String paymentMethod,
    OrderDetails orderDetails) {
  /** Converts the transport payload to an application command. */
  public CreateOrderCommand toCommand() {
    return new CreateOrderCommand(
        merchantReference,
        idempotencyKey,
        shopperDetails == null
            ? null
            : new ShopperDetailsCommand(
                shopperDetails.fullName(),
                shopperDetails.email(),
                shopperDetails.country(),
                shopperDetails.state(),
                shopperDetails.zipcode()),
        paymentMethod,
        orderDetails == null
            ? null
            : new OrderDetailsCommand(
                orderDetails.orderLines() == null
                    ? null
                    : orderDetails.orderLines().stream()
                        .map(
                            line ->
                                line == null
                                    ? null
                                    : new OrderLineCommand(
                                        line.merchantLineReference(),
                                        line.amount(),
                                        line.currency(),
                                        line.type()))
                        .toList(),
                orderDetails.totalAmount(),
                orderDetails.currency()));
  }

  /** Shopper information supplied for tax calculation and payment creation. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ShopperDetails(
      @Nullable String fullName,
      @Nullable String email,
      @Nullable String country,
      @Nullable String state,
      @Nullable String zipcode) {}

  /** Order totals and merchant line inputs. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record OrderDetails(
      @Nullable List<@Nullable OrderLine> orderLines,
      @Nullable Long totalAmount,
      @Nullable String currency) {}

  /** One net-priced order line. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record OrderLine(
      @Nullable String merchantLineReference,
      @Nullable Long amount,
      @Nullable String currency,
      @Nullable String type) {}
}
