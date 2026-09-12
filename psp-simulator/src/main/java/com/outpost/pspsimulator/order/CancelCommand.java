package com.outpost.pspsimulator.order;

/** The command that cancels an authorised order; its constructor validates the input. */
public record CancelCommand(long pspReference) {

  /** Validates the order reference to cancel. */
  public CancelCommand {
    if (pspReference <= 0) {
      throw new IllegalArgumentException("psp reference must be positive: " + pspReference);
    }
  }
}
