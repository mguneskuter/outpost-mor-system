package com.outpost.accounting.transaction;

import com.outpost.account.Account;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.TransactionTypes.TransactionType;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * A payment, or a capture or refund of one payment. A transaction that is not stored yet has no
 * identifier and no creation time.
 */
public final class Transaction {
  private final @Nullable Long transactionId;
  private final TransactionType transactionType;
  private final Account merchantAccount;
  private final String reference;
  private final Amount amount;
  private final @Nullable Instant createdAt;
  @Nullable private Transaction parentTransaction;
  private final List<Transaction> childTransactions = new ArrayList<>();

  private Transaction(
      @Nullable Long transactionId,
      TransactionType transactionType,
      Account merchantAccount,
      String reference,
      Amount amount,
      @Nullable Instant createdAt) {
    if (transactionId != null && transactionId <= 0) {
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
    this.createdAt = createdAt;
  }

  /** Creates a transaction; one that is not stored yet has no identifier and no creation time. */
  public static Transaction of(
      @Nullable Long transactionId,
      TransactionType transactionType,
      Account merchantAccount,
      String reference,
      Amount amount,
      @Nullable Instant createdAt) {
    return new Transaction(
        transactionId, transactionType, merchantAccount, reference, amount, createdAt);
  }

  /** Creates a transaction and attaches it as a direct child of a payment transaction. */
  public static Transaction childOf(
      Transaction parent,
      @Nullable Long transactionId,
      TransactionType transactionType,
      Account merchantAccount,
      String reference,
      Amount amount,
      @Nullable Instant createdAt) {
    Transaction child =
        of(transactionId, transactionType, merchantAccount, reference, amount, createdAt);
    Objects.requireNonNull(parent, "parent").attachChild(child);
    return child;
  }

  /** Returns the identifier; empty until the transaction is stored. */
  public OptionalLong getTransactionId() {
    return transactionId == null ? OptionalLong.empty() : OptionalLong.of(transactionId);
  }

  /** Returns the transaction type. */
  public TransactionType getTransactionType() {
    return transactionType;
  }

  /** Returns the merchant account. */
  public Account getMerchantAccount() {
    return merchantAccount;
  }

  /** Returns the transaction reference. */
  public String getReference() {
    return reference;
  }

  /** Returns the transaction amount. */
  public Amount getAmount() {
    return amount;
  }

  /** Returns when the transaction was stored; empty until it is stored. */
  public Optional<Instant> getCreatedAt() {
    return Optional.ofNullable(createdAt);
  }

  /** Returns the parent payment when this is a child transaction. */
  public Optional<Transaction> getParentTransaction() {
    return Optional.ofNullable(parentTransaction);
  }

  /** Returns the immutable direct children. */
  public List<Transaction> getChildTransactions() {
    return List.copyOf(childTransactions);
  }

  void attachChild(Transaction child) {
    Objects.requireNonNull(child, "child");
    if (!transactionType.equals(TransactionTypes.PAYMENT.getValue())) {
      throw new IllegalStateException("only a payment can own child transactions");
    }
    if (parentTransaction != null) {
      throw new IllegalStateException("a child transaction cannot own further children");
    }
    if (child.equals(this)) {
      throw new IllegalArgumentException("a transaction cannot parent itself");
    }
    if (child.transactionType.equals(TransactionTypes.PAYMENT.getValue())) {
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
    if (this == other) {
      return true;
    }
    if (!(other instanceof Transaction that)) {
      return false;
    }
    return Objects.equals(transactionId, that.transactionId)
        && Objects.equals(transactionType, that.transactionType)
        && Objects.equals(merchantAccount, that.merchantAccount)
        && Objects.equals(reference, that.reference)
        && Objects.equals(amount, that.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transactionId, transactionType, merchantAccount, reference, amount);
  }
}
