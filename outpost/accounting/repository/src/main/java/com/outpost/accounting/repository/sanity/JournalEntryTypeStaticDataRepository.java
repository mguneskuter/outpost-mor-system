package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.JournalEntryTypes.JournalEntryType;
import com.outpost.accounting.repository.JournalEntryTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

final class JournalEntryTypeStaticDataRepository
    implements StaticDataRepository<JournalEntryTypes, JournalEntryType, JournalEntryTypeRecord> {
  private final JournalEntryTypeStaticDataMapper mapper;

  JournalEntryTypeStaticDataRepository(JournalEntryTypeStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<JournalEntryTypes> staticDataEnum() {
    return JournalEntryTypes.class;
  }

  @Override
  public String table() {
    return "journal_entry_type";
  }

  @Override
  public JournalEntryType enumValue(JournalEntryTypes constant) {
    return constant.getValue();
  }

  @Override
  public JournalEntryTypeRecord toDatabaseRecord(JournalEntryType value) {
    return new JournalEntryTypeRecord(value.getJournalEntryTypeId(), value.getCode());
  }

  @Override
  public JournalEntryType toDomainValue(JournalEntryTypeRecord record) {
    return JournalEntryTypes.fromCode(record.code())
        .filter(v -> v.getJournalEntryTypeId() == record.journalEntryTypeId())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Journal entry type record does not match the enum: " + record));
  }

  @Override
  public long id(JournalEntryTypeRecord record) {
    return record.journalEntryTypeId();
  }

  @Override
  public List<JournalEntryTypeRecord> findAll() {
    return mapper.findAll();
  }
}
