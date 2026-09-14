package com.outpost.accounting.transactionlock.repository.mybatis;

import com.outpost.accounting.transactionlock.TransactionLock;
import com.outpost.accounting.transactionlock.repository.TransactionLockRepository;
import java.time.Duration;
import java.util.Optional;

/** Stores transaction locks in {@code transaction_lock}; each call is one statement. */
public final class MyBatisTransactionLockRepository implements TransactionLockRepository {
  private final TransactionLockMapper mapper;

  /** Creates a repository over the transaction lock mapper. */
  public MyBatisTransactionLockRepository(TransactionLockMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<TransactionLock> insertTransactionLock(
      String originalReference, Duration leaseDuration) {
    long seconds = leaseDuration.toSeconds();
    if (seconds < 1) {
      throw new IllegalArgumentException("leaseDuration must be at least one second");
    }
    return Optional.ofNullable(mapper.insertTransactionLock(originalReference, seconds));
  }

  @Override
  public void deleteTransactionLock(TransactionLock transactionLock) {
    mapper.deleteTransactionLock(transactionLock.originalReference(), transactionLock.lockedAt());
  }
}
