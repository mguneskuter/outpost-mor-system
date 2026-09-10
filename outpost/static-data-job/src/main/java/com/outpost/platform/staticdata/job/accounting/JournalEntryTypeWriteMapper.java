package com.outpost.platform.staticdata.job.accounting;

import com.outpost.accounting.repository.JournalEntryTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Reads and writes journal-entry-type rows. */
@RegisteredMapper
public interface JournalEntryTypeWriteMapper {
  /** Reads all journal-entry-type rows. */
  List<JournalEntryTypeRecord> findAll();

  /** Inserts one journal-entry-type row. */
  int insert(JournalEntryTypeRecord record);
}
