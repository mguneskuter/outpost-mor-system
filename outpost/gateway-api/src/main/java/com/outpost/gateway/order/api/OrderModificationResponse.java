package com.outpost.gateway.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.gateway.order.service.ModifyOrderResult;

/** JSON response for a submitted order modification request. */
public record OrderModificationResponse(
    @JsonProperty("refund_reference") String refundReference,
    @JsonProperty("status") String status) {
  /** Converts an application result to the transport response. */
  public static OrderModificationResponse from(ModifyOrderResult result) {
    return new OrderModificationResponse(result.refundReference(), result.status());
  }
}
