package com.outpost.gateway.order.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Application input for creating an order, as the request boundary validated it. */
public record CreateOrderCommand(
    String merchantReference,
    String idempotencyKey,
    ShopperDetailsCommand shopperDetails,
    String pspCode,
    OrderDetailsCommand orderDetails) {
  /** Shopper details at checkout. */
  public record ShopperDetailsCommand(
      String fullName,
      String email,
      String country,
      @Nullable String state,
      @Nullable String zipcode) {}

  /** Line inputs and order total. */
  public record OrderDetailsCommand(
      List<OrderLineCommand> orderLines, long totalAmount, String currency) {
    /** Copies the lines so the command stays immutable. */
    public OrderDetailsCommand {
      orderLines = List.copyOf(orderLines);
    }
  }

  /** A merchant-priced net line. */
  public record OrderLineCommand(
      String merchantLineReference, long amount, String currency, String type) {}
}
