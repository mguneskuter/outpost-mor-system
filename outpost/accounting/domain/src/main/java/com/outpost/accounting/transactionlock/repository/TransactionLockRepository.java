package com.outpost.accounting.transactionlock.repository;

import com.outpost.accounting.transactionlock.TransactionLock;
import java.time.Duration;
import java.util.Optional;

/** Takes and releases transaction locks. */
public interface TransactionLockRepository {
  /**
   * Takes the transaction lock for {@code originalReference} when it is free or its lease has
   * ended. The lease ends {@code leaseDuration} after the database time of the statement.
   *
   * @return the lock taken; empty when a live lock exists
   * @throws IllegalArgumentException when {@code leaseDuration} is shorter than one second
   */
  Optional<TransactionLock> insertTransactionLock(String originalReference, Duration leaseDuration);

  /**
   * Releases {@code transactionLock}. A lock another request took over after the lease ended is
   * kept.
   */
  void deleteTransactionLock(TransactionLock transactionLock);
}
