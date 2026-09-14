package com.outpost.accounting.transaction;

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
  public long getTransactionEventId() {
    return transactionEventId;
  }

  /** Returns the source transaction. */
  public Transaction getTransaction() {
    return transaction;
  }

  /** Returns the event type. */
  public TransactionEventType getTransactionEventType() {
    return transactionEventType;
  }

  /** Returns when the event occurred. */
  public Instant getOccurredAt() {
    return occurredAt;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof TransactionEvent that)) {
      return false;
    }
    return transactionEventId == that.transactionEventId
        && Objects.equals(transaction, that.transaction)
        && Objects.equals(transactionEventType, that.transactionEventType)
        && Objects.equals(occurredAt, that.occurredAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transactionEventId, transaction, transactionEventType, occurredAt);
  }
}
