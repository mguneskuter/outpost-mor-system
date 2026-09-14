package com.outpost.accounting.journalentry.repository.mybatis;

import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.JournalEntryLine;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis statements for journal entries and their lines. Package-private, like the stored forms it
 * returns.
 */
interface JournalEntryMapper {

  /**
   * Inserts the entry, booked and posted at the time of the database transaction that stores it,
   * and returns its generated identifier.
   */
  long insertJournalEntry(JournalEntry journalEntry);

  /** Inserts one line of the stored entry and returns the line's generated identifier. */
  long insertJournalEntryLine(
      @Param("journalEntryId") long journalEntryId,
      @Param("journalEntryLine") JournalEntryLine journalEntryLine);

  @Nullable PendingFee findPendingFeeByPayment(
      @Param("paymentTransactionId") long paymentTransactionId);

  @Nullable CaptureRegisters findCaptureRegistersByPayment(
      @Param("paymentTransactionId") long paymentTransactionId);
}
