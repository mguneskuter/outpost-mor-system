package com.outpost.accounting.transactionlock.repository.mybatis;

import com.outpost.accounting.transactionlock.TransactionLock;
import com.outpost.framework.persistence.RegisteredMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** MyBatis statements for the transaction lock. */
@RegisteredMapper
public interface TransactionLockMapper {
  /** Inserts or takes over an expired lock and returns it, or null when a live lock exists. */
  @Nullable TransactionLock insertTransactionLock(
      @Param("originalReference") String originalReference,
      @Param("leaseSeconds") long leaseSeconds);

  /** Deletes the lock taken at {@code lockedAt}; returns the number of rows deleted. */
  int deleteTransactionLock(
      @Param("originalReference") String originalReference, @Param("lockedAt") Instant lockedAt);
}
