package com.outpost.pspsimulator;

/** Signals that a request presented no valid API key, mapped to 401. */
public final class UnauthorizedException extends RuntimeException {

  /** Creates an authentication failure with a stable diagnostic message. */
  public UnauthorizedException(String message) {
    super(message);
  }
}
