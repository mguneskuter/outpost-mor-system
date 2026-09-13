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

  /**
   * {@inheritDoc}
   *
   * <p>The entry is stored booked and posted at the time of the database transaction that stores
   * it, which is the time of its event when both are written in one transaction.
   *
   * @throws IllegalStateException if the entry's booked or posted time differs from that
   *     transaction time; nothing is stored
   */
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
    if (!mapper.hasJournalEntryBookedAndPostedAt(
        journalEntryId, journalEntry.getBooked(), journalEntry.getPosted())) {
      throw new IllegalStateException(
          "journal entry is not booked and posted at the time of the transaction storing it");
    }
    return journalEntry.withIds(journalEntryId, journalEntryLineIds);
  }
}
