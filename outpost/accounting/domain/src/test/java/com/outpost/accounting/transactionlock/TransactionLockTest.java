package com.outpost.accounting.transactionlock;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionLockTest {
  private static final Instant LOCKED_AT = Instant.parse("2026-09-13T10:00:00Z");

  @Test
  void rejectsLeaseThatDoesNotEndAfterTheLock() {
    assertThrows(
        IllegalArgumentException.class, () -> new TransactionLock("order-1", LOCKED_AT, LOCKED_AT));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TransactionLock("order-1", LOCKED_AT, LOCKED_AT.minusSeconds(1)));
  }

  @Test
  void rejectsBlankOriginalReference() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TransactionLock(" ", LOCKED_AT, LOCKED_AT.plusSeconds(300)));
  }
}
