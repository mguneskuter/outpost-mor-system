package com.outpost.accounting.api;

/** The kinds of accounting request Gateway sends to the Ledger. */
public enum AccountingQueueRequestTypes {
  ORDER_CREATED,
  AUTHORISATION,
  CAPTURE,
  REFUND
}
