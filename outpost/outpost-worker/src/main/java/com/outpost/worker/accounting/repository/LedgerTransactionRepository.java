package com.outpost.worker.accounting.repository;

import java.util.Optional;

/**
 * The Worker's own narrow read of Ledger's transaction and transaction-event tables, needed to plan
 * a refund without importing Ledger's persistence implementation.
 */
public interface LedgerTransactionRepository {
  /** Returns whether the payment named by this reference has ever been captured. */
  boolean isCaptured(String paymentReference);

  /**
   * Returns the net and tax already active or confirmed for one order line, across every refund
   * except the one named by {@code excludingRefundReference}.
   */
  RefundedTotal activeRefundedTotal(long orderItemId, String excludingRefundReference);

  /** Returns the transaction identity for an exact reference, if one has been recorded. */
  Optional<Long> findTransactionId(String reference);

  /** The net and tax amounts already refunded, or reserved, for one order line. */
  record RefundedTotal(long net, long tax) {}
}
