package com.outpost.pspsimulator;

/** Signals that a request names something the simulator does not know, mapped to 404. */
public final class NotFoundException extends RuntimeException {

  /** Creates a not-found error with a stable diagnostic message. */
  public NotFoundException(String message) {
    super(message);
  }
}
