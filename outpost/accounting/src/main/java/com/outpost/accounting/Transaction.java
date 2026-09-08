package com.outpost.accounting;

import com.outpost.account.Account;
import com.outpost.accounting.TransactionTypes.TransactionType;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A transaction in a payment's one-level transaction family. */
public final class Transaction {
  private final long transactionId;
  private final TransactionType transactionType;
  private final Account merchantAccount;
  private final String reference;
  private final Amount amount;
  private final Instant createdAt;
  @Nullable private Transaction parentTransaction;
  private final List<Transaction> childTransactions = new ArrayList<>();

  Transaction(
      long transactionId,
      TransactionType transactionType,
      Account merchantAccount,
      String reference,
      Amount amount,
      Instant createdAt) {
    if (transactionId <= 0) {
      throw new IllegalArgumentException("transactionId must be positive: " + transactionId);
    }
    this.transactionId = transactionId;
    this.transactionType = Objects.requireNonNull(transactionType, "transactionType");
    this.merchantAccount = Objects.requireNonNull(merchantAccount, "merchantAccount");
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be null or blank");
    }
    this.reference = reference;
    this.amount = Objects.requireNonNull(amount, "amount");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  /** Returns the transaction identity. */
  public long transactionId() {
    return transactionId;
  }

  /** Returns the transaction family. */
  public TransactionType transactionType() {
    return transactionType;
  }

  /** Returns the merchant account. */
  public Account merchantAccount() {
    return merchantAccount;
  }

  /** Returns the transaction reference. */
  public String reference() {
    return reference;
  }

  /** Returns the transaction amount. */
  public Amount amount() {
    return amount;
  }

  /** Returns when the transaction was created. */
  public Instant createdAt() {
    return createdAt;
  }

  /** Returns the parent payment when this is a child transaction. */
  public Optional<Transaction> parentTransaction() {
    return Optional.ofNullable(parentTransaction);
  }

  /** Returns the immutable direct children. */
  public List<Transaction> childTransactions() {
    return List.copyOf(childTransactions);
  }

  void attachChild(Transaction child) {
    Objects.requireNonNull(child, "child");
    if (!transactionType.equals(TransactionTypes.PAYMENT.value())) {
      throw new IllegalStateException("only a payment can own child transactions");
    }
    if (parentTransaction != null) {
      throw new IllegalStateException("a child transaction cannot own further children");
    }
    if (child.equals(this)) {
      throw new IllegalArgumentException("a transaction cannot parent itself");
    }
    if (child.transactionType.equals(TransactionTypes.PAYMENT.value())) {
      throw new IllegalArgumentException("a payment cannot be a child transaction");
    }
    if (child.parentTransaction != null) {
      throw new IllegalStateException("a child transaction already has a parent");
    }
    if (childTransactions.contains(child)) {
      throw new IllegalArgumentException("duplicate child transaction identity");
    }
    child.parentTransaction = this;
    childTransactions.add(child);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof Transaction that && transactionId == that.transactionId);
  }

  @Override
  public int hashCode() {
    return Long.hashCode(transactionId);
  }
}
