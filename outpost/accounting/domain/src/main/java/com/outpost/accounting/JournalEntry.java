package com.outpost.accounting;

import com.outpost.accounting.JournalEntryTypes.JournalEntryType;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** A journal entry owned by one source transaction event. */
public final class JournalEntry {
  private final long journalEntryId;
  private final TransactionEvent transactionEvent;
  private final JournalEntryType journalEntryType;
  private final Instant booked;
  private final Instant posted;
  private final Set<JournalEntryLine> journalEntryLines = new LinkedHashSet<>();

  /** Creates a journal entry. */
  public JournalEntry(
      long journalEntryId,
      TransactionEvent transactionEvent,
      JournalEntryType journalEntryType,
      Instant booked,
      Instant posted) {
    if (journalEntryId <= 0) {
      throw new IllegalArgumentException("journalEntryId must be positive: " + journalEntryId);
    }
    this.journalEntryId = journalEntryId;
    this.transactionEvent = Objects.requireNonNull(transactionEvent, "transactionEvent");
    this.journalEntryType = Objects.requireNonNull(journalEntryType, "journalEntryType");
    this.booked = Objects.requireNonNull(booked, "booked");
    this.posted = Objects.requireNonNull(posted, "posted");
  }

  /** Returns the entry identity. */
  public long getJournalEntryId() {
    return journalEntryId;
  }

  /** Returns the source event. */
  public TransactionEvent getTransactionEvent() {
    return transactionEvent;
  }

  /** Returns the entry type. */
  public JournalEntryType getJournalEntryType() {
    return journalEntryType;
  }

  /** Returns when the entry was booked. */
  public Instant getBooked() {
    return booked;
  }

  /** Returns when the entry was posted. */
  public Instant getPosted() {
    return posted;
  }

  /** Returns immutable owned journal-entry lines. */
  public Set<JournalEntryLine> getJournalEntryLines() {
    return Set.copyOf(journalEntryLines);
  }

  @SuppressWarnings("ReferenceEquality")
  void addLine(JournalEntryLine journalEntryLine) {
    Objects.requireNonNull(journalEntryLine, "journalEntryLine");
    if (journalEntryLine.getJournalEntry() != this) {
      throw new IllegalArgumentException("a journal entry line must belong to this journal entry");
    }
    if (!journalEntryLines.add(journalEntryLine)) {
      throw new IllegalArgumentException("duplicate journal entry line identity");
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof JournalEntry that)) {
      return false;
    }
    return journalEntryId == that.journalEntryId
        && Objects.equals(transactionEvent, that.transactionEvent)
        && Objects.equals(journalEntryType, that.journalEntryType)
        && Objects.equals(booked, that.booked)
        && Objects.equals(posted, that.posted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(journalEntryId, transactionEvent, journalEntryType, booked, posted);
  }
}
