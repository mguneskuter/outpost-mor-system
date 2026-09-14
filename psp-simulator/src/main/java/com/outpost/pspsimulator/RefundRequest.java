package com.outpost.pspsimulator;

import com.outpost.pspsimulator.refund.RefundCommand;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** A refund request: the order's PSP reference and the caller's refund reference. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RefundRequest(String pspReference, String refundReference) {

  /** Returns the request as a refund command. */
  public RefundCommand toCommand() {
    return new RefundCommand(pspReference, refundReference);
  }
}
