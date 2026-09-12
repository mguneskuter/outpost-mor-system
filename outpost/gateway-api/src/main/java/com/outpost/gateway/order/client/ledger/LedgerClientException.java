package com.outpost.gateway.order.client.ledger;

/** Failure returned while calling Ledger. */
public final class LedgerClientException extends RuntimeException {
  private final boolean retryable;

  /** Creates a classified Ledger failure. */
  public LedgerClientException(String message, boolean retryable, Throwable cause) {
    super(message, cause);
    this.retryable = retryable;
  }

  /** Returns whether retrying the same stable payment reference is safe. */
  public boolean retryable() {
    return retryable;
  }
}
