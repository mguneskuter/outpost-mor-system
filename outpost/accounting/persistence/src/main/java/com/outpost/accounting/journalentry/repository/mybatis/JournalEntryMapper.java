package com.outpost.accounting.journalentry.repository.mybatis;

import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.framework.persistence.RegisteredMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** MyBatis statements for journal entries and their lines. */
@RegisteredMapper
public interface JournalEntryMapper {

  /**
   * Inserts the entry, booked and posted at the time of the database transaction that stores it,
   * and returns its generated identifier.
   */
  long insertJournalEntry(JournalEntry journalEntry);

  /** Returns whether the stored entry was booked at {@code booked} and posted at {@code posted}. */
  boolean hasJournalEntryBookedAndPostedAt(
      @Param("journalEntryId") long journalEntryId,
      @Param("booked") Instant booked,
      @Param("posted") Instant posted);

  /** Inserts one line of the stored entry and returns the line's generated identifier. */
  long insertJournalEntryLine(
      @Param("journalEntryId") long journalEntryId,
      @Param("journalEntryLine") JournalEntryLine journalEntryLine);
}
