package com.outpost.accounting.journalentry.repository;

import com.outpost.accounting.journalentry.CaptureRegisters;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.PendingFee;
import com.outpost.accounting.transaction.Transaction;
import java.util.Optional;

/** Stores journal entries and reads what later bookings of a payment need from them. */
public interface JournalEntryRepository {

  /**
   * Stores {@code journalEntry} and all of its lines as one unit: either the entry and every line
   * are stored, or none is.
   *
   * @return the stored entry, carrying the entry identifier and one line identifier per line
   */
  JournalEntry insertJournalEntry(JournalEntry journalEntry);

  /** Finds the pending fee the FEE_PENDING entry of a stored payment holds. */
  Optional<PendingFee> findPendingFeeByPayment(Transaction payment);

  /** Finds the registers the CAPTURE entry of a stored payment's capture posted to. */
  Optional<CaptureRegisters> findCaptureRegistersByPayment(Transaction payment);
}
