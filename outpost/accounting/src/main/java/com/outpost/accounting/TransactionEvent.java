package com.outpost.accounting;

import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import java.time.Instant;
import java.util.Objects;

/** An immutable fact observed for a transaction. */
public final class TransactionEvent {
  private final long transactionEventId;
  private final Transaction transaction;
  private final TransactionEventType transactionEventType;
  private final Instant occurredAt;

  /** Creates a transaction event. */
  public TransactionEvent(
      long transactionEventId,
      Transaction transaction,
      TransactionEventType transactionEventType,
      Instant occurredAt) {
    if (transactionEventId <= 0) {
      throw new IllegalArgumentException(
          "transactionEventId must be positive: " + transactionEventId);
    }
    this.transactionEventId = transactionEventId;
    this.transaction = Objects.requireNonNull(transaction, "transaction");
    this.transactionEventType =
        Objects.requireNonNull(transactionEventType, "transactionEventType");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  /** Returns the event identity. */
  public long transactionEventId() {
    return transactionEventId;
  }

  /** Returns the source transaction. */
  public Transaction transaction() {
    return transaction;
  }

  /** Returns the event type. */
  public TransactionEventType transactionEventType() {
    return transactionEventType;
  }

  /** Returns when the event occurred. */
  public Instant occurredAt() {
    return occurredAt;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof TransactionEvent that
            && transactionEventId == that.transactionEventId);
  }

  @Override
  public int hashCode() {
    return Long.hashCode(transactionEventId);
  }
}
