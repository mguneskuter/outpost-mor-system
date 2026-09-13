package com.outpost.accounting;

import com.outpost.accounting.JournalEntryTypes.JournalEntryType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A journal entry owned by one source transaction event.
 *
 * <p>A journal template builds the entry without identifiers; storing it yields a copy that carries
 * the stored entry and line identifiers ({@link #withIds}). Lines keep the order in which they were
 * added, and two lines with the same register and amount remain two lines.
 */
public final class JournalEntry {
  private final @Nullable Long journalEntryId;
  private final TransactionEvent transactionEvent;
  private final JournalEntryType journalEntryType;
  private final Instant booked;
  private final Instant posted;
  private final List<JournalEntryLine> journalEntryLines = new ArrayList<>();

  /** Creates an entry that has not been stored. */
  public JournalEntry(
      TransactionEvent transactionEvent,
      JournalEntryType journalEntryType,
      Instant booked,
      Instant posted) {
    this(null, transactionEvent, journalEntryType, booked, posted);
  }

  private JournalEntry(
      @Nullable Long journalEntryId,
      TransactionEvent transactionEvent,
      JournalEntryType journalEntryType,
      Instant booked,
      Instant posted) {
    if (journalEntryId != null && journalEntryId <= 0) {
      throw new IllegalArgumentException("journalEntryId must be positive: " + journalEntryId);
    }
    this.journalEntryId = journalEntryId;
    this.transactionEvent = Objects.requireNonNull(transactionEvent, "transactionEvent");
    this.journalEntryType = Objects.requireNonNull(journalEntryType, "journalEntryType");
    this.booked = Objects.requireNonNull(booked, "booked");
    this.posted = Objects.requireNonNull(posted, "posted");
  }

  /** Returns the stored entry identifier, or empty before the entry is stored. */
  public Optional<Long> getJournalEntryId() {
    return Optional.ofNullable(journalEntryId);
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

  /** Returns an immutable copy of the lines, in the order they were added. */
  public List<JournalEntryLine> getJournalEntryLines() {
    return List.copyOf(journalEntryLines);
  }

  /**
   * Appends a line to an entry that has not been stored. Only journal templates put lines on an
   * entry.
   *
   * @throws IllegalArgumentException if the line belongs to another entry or is already on this
   *     entry
   * @throws IllegalStateException if this entry has been stored
   */
  @SuppressWarnings("ReferenceEquality")
  public void addLine(JournalEntryLine journalEntryLine) {
    Objects.requireNonNull(journalEntryLine, "journalEntryLine");
    if (journalEntryId != null) {
      throw new IllegalStateException("a stored journal entry takes no new lines");
    }
    if (journalEntryLine.getJournalEntry() != this) {
      throw new IllegalArgumentException("a journal entry line must belong to this journal entry");
    }
    for (JournalEntryLine line : journalEntryLines) {
      if (line == journalEntryLine) {
        throw new IllegalArgumentException("the journal entry line is already on this entry");
      }
    }
    journalEntryLines.add(journalEntryLine);
  }

  /**
   * Returns a copy of this entry carrying the identifiers assigned when it was stored, with one
   * line identifier for each line in line order.
   *
   * @throws IllegalStateException if this entry already carries an identifier
   * @throws IllegalArgumentException if an identifier is not positive, or the line identifiers do
   *     not match the lines one to one
   */
  public JournalEntry withIds(long journalEntryId, List<Long> journalEntryLineIds) {
    if (this.journalEntryId != null) {
      throw new IllegalStateException("the journal entry already carries an identifier");
    }
    List<Long> lineIds = List.copyOf(journalEntryLineIds);
    if (lineIds.size() != journalEntryLines.size()) {
      throw new IllegalArgumentException(
          "expected " + journalEntryLines.size() + " line identifiers: " + lineIds.size());
    }
    JournalEntry stored =
        new JournalEntry(journalEntryId, transactionEvent, journalEntryType, booked, posted);
    for (int index = 0; index < lineIds.size(); index++) {
      JournalEntryLine line = journalEntryLines.get(index);
      stored.journalEntryLines.add(
          new JournalEntryLine(lineIds.get(index), stored, line.getRegister(), line.getAmount()));
    }
    return stored;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof JournalEntry that)) {
      return false;
    }
    return Objects.equals(journalEntryId, that.journalEntryId)
        && transactionEvent.equals(that.transactionEvent)
        && journalEntryType.equals(that.journalEntryType)
        && booked.equals(that.booked)
        && posted.equals(that.posted);
  }

  @Override
  public int hashCode() {
    return Objects.hash(journalEntryId, transactionEvent, journalEntryType, booked, posted);
  }
}
