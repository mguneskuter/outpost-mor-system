package com.outpost.pspsimulator;

import com.outpost.pspsimulator.order.CancelCommand;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** A cancellation request naming the order's PSP reference. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CancelRequest(String pspReference) {

  /** Returns the request as a cancellation command, parsing the reference for the domain. */
  public CancelCommand toCommand() {
    return new CancelCommand(Long.parseLong(pspReference));
  }
}
