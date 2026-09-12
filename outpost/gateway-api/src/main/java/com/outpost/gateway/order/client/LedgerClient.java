package com.outpost.gateway.order.client;

/** Creates immutable payment evidence in Ledger. */
public interface LedgerClient {
  /** Creates or replays the payment identified by its stable reference. */
  void createPayment(LedgerPayment payment);
}
