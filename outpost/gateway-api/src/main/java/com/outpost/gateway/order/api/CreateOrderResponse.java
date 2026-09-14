package com.outpost.gateway.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.gateway.order.service.CreateOrderResult;
import java.time.Instant;
import java.util.List;

/** JSON response for a created payment order. */
public record CreateOrderResponse(
    @JsonProperty("order_reference") String orderReference,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("payment_details") PaymentDetails paymentDetails,
    @JsonProperty("order_lines") List<OrderLine> orderLines) {
  /** Copies the lines so the response cannot change after it is created. */
  public CreateOrderResponse {
    orderLines = List.copyOf(orderLines);
  }

  /** Converts an application result to the transport response. */
  public static CreateOrderResponse from(CreateOrderResult result) {
    return new CreateOrderResponse(
        result.orderReference(),
        result.createdAt(),
        new PaymentDetails(
            result.netAmount(),
            result.currency(),
            result.taxAmount(),
            result.grossAmount(),
            result.paymentLink()),
        result.lines().stream()
            .map(
                line ->
                    new OrderLine(
                        line.orderLineReference(),
                        line.merchantLineReference(),
                        line.netAmount(),
                        line.taxAmount(),
                        line.grossAmount(),
                        line.taxRate()))
            .toList());
  }

  /** The persisted payment totals and link. */
  public record PaymentDetails(
      long amount,
      String currency,
      @JsonProperty("tax_amount") long taxAmount,
      @JsonProperty("total_amount") long totalAmount,
      @JsonProperty("payment_link") String paymentLink) {}

  /** The persisted line breakdown. */
  public record OrderLine(
      @JsonProperty("order_line_reference") String orderLineReference,
      @JsonProperty("merchant_line_reference") String merchantLineReference,
      long amount,
      @JsonProperty("tax_amount") long taxAmount,
      @JsonProperty("total_amount") long totalAmount,
      @JsonProperty("tax_rate") String taxRate) {}
}
