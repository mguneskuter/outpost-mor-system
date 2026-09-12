package com.outpost.pspsimulator;

import com.outpost.pspsimulator.order.CreateOrderCommand;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** A create-order request: the payment reference, the amount in minor units, and the currency. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateOrderRequest(String paymentReference, long amount, String currency) {

  /** Returns the request as a create-order command. */
  public CreateOrderCommand toCommand() {
    return new CreateOrderCommand(paymentReference, amount, currency);
  }
}
