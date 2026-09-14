package com.outpost.integration.psp;

import org.jspecify.annotations.Nullable;

/**
 * A PSP call whose result is unknown: the PSP may or may not have applied it, because it did not
 * answer in time, the call failed in transport, or its answer could not be read.
 */
public final class UnknownPspResultException extends RuntimeException {

  /** Creates the failure with the transport cause, when there is one. */
  public UnknownPspResultException(@Nullable Throwable cause) {
    super("PSP result is unknown", cause);
  }
}
