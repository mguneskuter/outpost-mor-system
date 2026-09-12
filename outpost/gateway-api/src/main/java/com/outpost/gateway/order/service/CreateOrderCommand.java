package com.outpost.gateway.order.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Application input for creating an order. */
public record CreateOrderCommand(
    @Nullable String merchantReference,
    @Nullable String idempotencyKey,
    @Nullable ShopperDetailsCommand shopperDetails,
    @Nullable String paymentMethod,
    @Nullable OrderDetailsCommand orderDetails) {
  /** Shopper details at checkout. */
  public record ShopperDetailsCommand(
      @Nullable String fullName,
      @Nullable String email,
      @Nullable String country,
      @Nullable String state,
      @Nullable String zipcode) {}

  /** Line inputs and order total. */
  public record OrderDetailsCommand(
      @Nullable List<@Nullable OrderLineCommand> orderLines,
      @Nullable Long totalAmount,
      @Nullable String currency) {}

  /** A merchant-priced net line. */
  public record OrderLineCommand(
      @Nullable String merchantLineReference,
      @Nullable Long amount,
      @Nullable String currency,
      @Nullable String type) {}
}
