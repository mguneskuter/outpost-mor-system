package com.outpost.gateway.report.client.ledger;

/** Failure returned while calling Ledger's balance report routes. */
public final class LedgerReportClientException extends RuntimeException {
  /** Creates a wrapped Ledger report failure. */
  public LedgerReportClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
