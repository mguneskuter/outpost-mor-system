package com.outpost.pspsimulator;

import com.outpost.pspsimulator.order.PayCommand;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** A card-payment request: the PSP reference of the order and a documented test card number. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PayRequest(String pspReference, String cardNumber) {

  /** Returns the request as a payment command. */
  public PayCommand toCommand() {
    return new PayCommand(pspReference, cardNumber);
  }
}
