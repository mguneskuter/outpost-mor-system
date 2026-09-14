package com.outpost.backoffice.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The Gateway's order request, as the merchant sends it. */
public record CreateOrderRequest(
    @JsonProperty("merchant_reference") String merchantReference,
    @JsonProperty("idempotency_key") String idempotencyKey,
    @JsonProperty("psp_code") String pspCode,
    @JsonProperty("shopper_details") Shopper shopperDetails,
    @JsonProperty("order_details") OrderDetails orderDetails) {
  /** The shopper the order is for; state and postal code are optional. */
  public record Shopper(
      @JsonProperty("full_name") String fullName,
      @JsonProperty("email") String email,
      @JsonProperty("country") String country,
      @JsonProperty("state") @Nullable String state,
      @JsonProperty("zipcode") @Nullable String zipcode) {}

  /** The net lines and their total in one currency. */
  public record OrderDetails(
      @JsonProperty("order_lines") List<Line> orderLines,
      @JsonProperty("total_amount") long totalAmount,
      @JsonProperty("currency") String currency) {}

  /** One net-priced line. */
  public record Line(
      @JsonProperty("merchant_line_reference") String merchantLineReference,
      @JsonProperty("amount") long amount,
      @JsonProperty("currency") String currency,
      @JsonProperty("type") String type) {}
}
