package com.outpost.accounting.transactionlock;

import java.time.Instant;
import java.util.Objects;

/**
 * A held transaction lock: while {@code leaseUntil} has not passed, no other accounting request for
 * the payment identified by {@code originalReference} is accepted.
 *
 * @param lockedAt the database time the lock was taken; it identifies this holder
 */
public record TransactionLock(String originalReference, Instant lockedAt, Instant leaseUntil) {
  /**
   * Rejects a lock that identifies no payment or whose lease has already ended when taken.
   *
   * @throws IllegalArgumentException when {@code originalReference} is blank or {@code leaseUntil}
   *     is not after {@code lockedAt}
   */
  public TransactionLock {
    Objects.requireNonNull(originalReference, "originalReference");
    Objects.requireNonNull(lockedAt, "lockedAt");
    Objects.requireNonNull(leaseUntil, "leaseUntil");
    if (originalReference.isBlank()) {
      throw new IllegalArgumentException("originalReference must not be blank");
    }
    if (!leaseUntil.isAfter(lockedAt)) {
      throw new IllegalArgumentException("leaseUntil must be after lockedAt");
    }
  }
}
