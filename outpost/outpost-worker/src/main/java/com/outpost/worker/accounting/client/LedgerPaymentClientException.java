package com.outpost.worker.accounting.client;

/** Failure returned while calling Ledger's payment routes. */
public final class LedgerPaymentClientException extends RuntimeException {
  /** Creates a Ledger payment call failure, keeping the original cause. */
  public LedgerPaymentClientException(String message, Throwable cause) {
    super(message, cause);
  }

  /** Creates a Ledger payment call failure with no lower-level cause. */
  public LedgerPaymentClientException(String message) {
    super(message);
  }
}
