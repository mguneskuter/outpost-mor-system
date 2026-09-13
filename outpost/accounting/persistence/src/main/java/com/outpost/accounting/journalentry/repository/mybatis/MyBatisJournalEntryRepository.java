package com.outpost.accounting.journalentry.repository.mybatis;

import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** MyBatis adapter for journal entries. */
public final class MyBatisJournalEntryRepository implements JournalEntryRepository {
  private final JournalEntryMapper mapper;
  private final TransactionTemplate transactionTemplate;

  /**
   * Creates an adapter whose inserts run in one transaction; a call joins the caller's transaction
   * when one is active.
   */
  public MyBatisJournalEntryRepository(
      JournalEntryMapper mapper, PlatformTransactionManager transactionManager) {
    this.mapper = mapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public JournalEntry insertJournalEntry(JournalEntry journalEntry) {
    return Objects.requireNonNull(
        transactionTemplate.execute(status -> insertEntryAndLines(journalEntry)));
  }

  private JournalEntry insertEntryAndLines(JournalEntry journalEntry) {
    long journalEntryId = mapper.insertJournalEntry(journalEntry);
    List<Long> journalEntryLineIds = new ArrayList<>();
    for (JournalEntryLine line : journalEntry.getJournalEntryLines()) {
      journalEntryLineIds.add(mapper.insertJournalEntryLine(journalEntryId, line));
    }
    return journalEntry.withIds(journalEntryId, journalEntryLineIds);
  }
}
