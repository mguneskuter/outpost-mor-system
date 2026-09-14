package com.outpost.gateway.order.service;

import java.time.Instant;
import java.util.List;

/** Application result reconstructed from durable order facts. */
public record CreateOrderResult(
    String orderReference,
    Instant createdAt,
    long netAmount,
    String currency,
    long taxAmount,
    long grossAmount,
    String paymentLink,
    List<OrderLineResult> lines) {
  /** Copies the lines so the result cannot change after it is created. */
  public CreateOrderResult {
    lines = List.copyOf(lines);
  }

  /** One persisted line and its tax calculation. */
  public record OrderLineResult(
      String orderLineReference,
      String merchantLineReference,
      long netAmount,
      long taxAmount,
      long grossAmount,
      String taxRate) {}
}
