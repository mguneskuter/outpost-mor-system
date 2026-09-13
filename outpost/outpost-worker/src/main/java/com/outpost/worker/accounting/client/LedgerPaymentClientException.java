package com.outpost.worker.accounting.client;

/** Failure returned while calling Ledger's payment routes. */
public final class LedgerPaymentClientException extends RuntimeException {
  /** Creates a Ledger payment call failure from the transport or HTTP error that caused it. */
  public LedgerPaymentClientException(Throwable cause) {
    super(cause);
  }

  /** Creates a Ledger payment call failure with no lower-level cause. */
  public LedgerPaymentClientException(String message) {
    super(message);
  }
}
