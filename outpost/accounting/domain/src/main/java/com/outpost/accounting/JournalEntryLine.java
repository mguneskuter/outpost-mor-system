package com.outpost.accounting;

import com.outpost.payment.common.Amount;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** One currency-denominated movement in a journal entry. */
public final class JournalEntryLine {
  private final @Nullable Long journalEntryLineId;
  private final JournalEntry journalEntry;
  private final Register register;
  private final Amount amount;

  /** Creates a line that has not been stored. */
  public JournalEntryLine(JournalEntry journalEntry, Register register, Amount amount) {
    this(null, journalEntry, register, amount);
  }

  JournalEntryLine(
      @Nullable Long journalEntryLineId,
      JournalEntry journalEntry,
      Register register,
      Amount amount) {
    if (journalEntryLineId != null && journalEntryLineId <= 0) {
      throw new IllegalArgumentException(
          "journalEntryLineId must be positive: " + journalEntryLineId);
    }
    this.journalEntryLineId = journalEntryLineId;
    this.journalEntry = Objects.requireNonNull(journalEntry, "journalEntry");
    this.register = Objects.requireNonNull(register, "register");
    this.amount = Objects.requireNonNull(amount, "amount");
  }

  /** Returns the stored line identifier, or empty before the line is stored. */
  public Optional<Long> getJournalEntryLineId() {
    return Optional.ofNullable(journalEntryLineId);
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
    return Objects.equals(journalEntryLineId, that.journalEntryLineId)
        && journalEntry.equals(that.journalEntry)
        && register.equals(that.register)
        && amount.equals(that.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(journalEntryLineId, journalEntry, register, amount);
  }
}
