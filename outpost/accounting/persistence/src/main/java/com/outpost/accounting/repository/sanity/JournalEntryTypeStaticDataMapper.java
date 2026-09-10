package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.repository.JournalEntryTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads journal-entry-type rows. */
@RegisteredMapper
public interface JournalEntryTypeStaticDataMapper {
  /** Reads all journal-entry-type rows. */
  @Select(
      "SELECT journal_entry_type_id, code FROM journal_entry_type ORDER BY journal_entry_type_id")
  List<JournalEntryTypeRecord> findAll();
}
