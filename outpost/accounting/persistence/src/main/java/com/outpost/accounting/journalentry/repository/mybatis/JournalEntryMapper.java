package com.outpost.accounting.journalentry.repository.mybatis;

import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.framework.persistence.RegisteredMapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis statements for journal entries and their lines. */
@RegisteredMapper
public interface JournalEntryMapper {

  /** Inserts the entry and returns its generated identifier. */
  long insertJournalEntry(JournalEntry journalEntry);

  /** Inserts one line of the stored entry and returns the line's generated identifier. */
  long insertJournalEntryLine(
      @Param("journalEntryId") long journalEntryId,
      @Param("journalEntryLine") JournalEntryLine journalEntryLine);
}
