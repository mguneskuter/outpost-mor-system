package com.outpost.gateway.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.gateway.order.service.OrderModificationResult;

/** JSON response for a refund the PSP accepted. */
public record OrderModificationResponse(@JsonProperty("refund_reference") String refundReference) {
  /** Converts an application result to the transport response. */
  public static OrderModificationResponse from(OrderModificationResult result) {
    return new OrderModificationResponse(result.refundReference());
  }
}
