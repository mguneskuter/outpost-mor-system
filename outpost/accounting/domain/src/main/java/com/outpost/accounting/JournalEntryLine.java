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
  public long getJournalEntryLineId() {
    return journalEntryLineId;
  }

  /** Returns the owning entry. */
  public JournalEntry getJournalEntry() {
    return journalEntry;
  }

  /** Returns the target register. */
  public Register getRegister() {
    return register;
  }

  /** Returns the signed amount. */
  public Amount getAmount() {
    return amount;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof JournalEntryLine that)) {
      return false;
    }
    return journalEntryLineId == that.journalEntryLineId
        && Objects.equals(journalEntry, that.journalEntry)
        && Objects.equals(register, that.register)
        && Objects.equals(amount, that.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(journalEntryLineId, journalEntry, register, amount);
  }
}
