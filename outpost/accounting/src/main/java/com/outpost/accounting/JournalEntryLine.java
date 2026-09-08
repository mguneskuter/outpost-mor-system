package com.outpost.accounting;

import com.outpost.payment.common.Amount;
import java.util.Objects;

/** One currency-denominated movement in a journal entry. */
public final class JournalEntryLine {
  private final long journalEntryLineId;
  private final JournalEntry journalEntry;
  private final Register register;
  private final Amount amount;

  /** Creates a journal entry line. */
  public JournalEntryLine(
      long journalEntryLineId, JournalEntry journalEntry, Register register, Amount amount) {
    if (journalEntryLineId <= 0) {
      throw new IllegalArgumentException(
          "journalEntryLineId must be positive: " + journalEntryLineId);
    }
    this.journalEntryLineId = journalEntryLineId;
    this.journalEntry = Objects.requireNonNull(journalEntry, "journalEntry");
    this.register = Objects.requireNonNull(register, "register");
    this.amount = Objects.requireNonNull(amount, "amount");
  }

  /** Returns the line identity. */
  public long journalEntryLineId() {
    return journalEntryLineId;
  }

  /** Returns the owning entry. */
  public JournalEntry journalEntry() {
    return journalEntry;
  }

  /** Returns the target register. */
  public Register register() {
    return register;
  }

  /** Returns the signed amount. */
  public Amount amount() {
    return amount;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof JournalEntryLine that
            && journalEntryLineId == that.journalEntryLineId);
  }

  @Override
  public int hashCode() {
    return Long.hashCode(journalEntryLineId);
  }
}
