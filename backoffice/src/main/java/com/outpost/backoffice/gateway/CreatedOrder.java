package com.outpost.backoffice.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** The Gateway's answer to a created order. */
public record CreatedOrder(
    @JsonProperty("order_reference") String orderReference,
    @JsonProperty("payment_details") PaymentDetails paymentDetails,
    @JsonProperty("order_lines") List<Line> orderLines) {
  /** The priced totals and the page where the shopper pays. */
  public record PaymentDetails(
      @JsonProperty("amount") long amount,
      @JsonProperty("currency") String currency,
      @JsonProperty("tax_amount") long taxAmount,
      @JsonProperty("total_amount") long totalAmount,
      @JsonProperty("payment_link") String paymentLink) {}

  /** One priced line. */
  public record Line(
      @JsonProperty("merchant_line_reference") String merchantLineReference,
      @JsonProperty("amount") long amount,
      @JsonProperty("tax_amount") long taxAmount,
      @JsonProperty("total_amount") long totalAmount,
      @JsonProperty("tax_rate") String taxRate) {}
}
