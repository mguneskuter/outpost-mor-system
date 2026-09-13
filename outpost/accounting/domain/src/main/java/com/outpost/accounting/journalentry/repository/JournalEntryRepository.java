package com.outpost.accounting.journalentry.repository;

import com.outpost.accounting.JournalEntry;

/** Stores journal entries. */
public interface JournalEntryRepository {

  /**
   * Stores {@code journalEntry} and all of its lines as one unit: either the entry and every line
   * are stored, or none is.
   *
   * @return the stored entry, carrying the entry identifier and one line identifier per line
   */
  JournalEntry insertJournalEntry(JournalEntry journalEntry);
}
