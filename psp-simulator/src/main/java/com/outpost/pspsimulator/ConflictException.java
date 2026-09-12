package com.outpost.pspsimulator;

/** Signals that a request contradicts stored state, mapped to 409. */
public final class ConflictException extends RuntimeException {

  /** Creates a conflict error with a stable diagnostic message. */
  public ConflictException(String message) {
    super(message);
  }
}
