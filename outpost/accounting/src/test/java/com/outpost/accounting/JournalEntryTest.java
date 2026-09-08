package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JournalEntryTest {
  @Test
  void entryOwnsOnlyItsLinesAndExposesAnImmutableView() {
    JournalEntry entry = entry(1L);
    JournalEntryLine line = line(1L, entry);

    entry.addLine(line);

    assertEquals(1, entry.journalEntryLines().size());
    assertThrows(UnsupportedOperationException.class, () -> entry.journalEntryLines().add(line));
  }

  @Test
  void entryRejectsForeignOwnershipAndDuplicateLineIdentitiesWithoutMutation() {
    JournalEntry entry = entry(1L);
    JournalEntry foreignEntry = entry(2L);
    JournalEntry sameIdentityDifferentInstance = entry(1L);
    JournalEntryLine foreignLine = line(1L, foreignEntry);
    JournalEntryLine sameIdentityForeignLine = line(2L, sameIdentityDifferentInstance);
    final JournalEntryLine firstLine = line(3L, entry);
    final JournalEntryLine duplicateIdentity = line(3L, entry);

    assertThrows(IllegalArgumentException.class, () -> entry.addLine(foreignLine));
    assertThrows(IllegalArgumentException.class, () -> entry.addLine(sameIdentityForeignLine));
    assertTrue(entry.journalEntryLines().isEmpty());
    entry.addLine(firstLine);
    assertThrows(IllegalArgumentException.class, () -> entry.addLine(duplicateIdentity));
    assertEquals(1, entry.journalEntryLines().size());
  }

  static JournalEntry entry(long journalEntryId) {
    TransactionEvent event =
        new TransactionEvent(
            journalEntryId,
            AccountingFixtures.payment(journalEntryId),
            TransactionEventTypes.CAPTURED.value(),
            AccountingFixtures.CREATED);
    return new JournalEntry(
        journalEntryId,
        event,
        JournalEntryTypes.CAPTURE.value(),
        AccountingFixtures.CREATED,
        AccountingFixtures.CREATED);
  }

  private static JournalEntryLine line(long journalEntryLineId, JournalEntry entry) {
    return new JournalEntryLine(
        journalEntryLineId,
        entry,
        new Register(
            journalEntryLineId,
            AccountingFixtures.merchant(),
            RegisterTypes.MERCHANT_PAYABLE.value()),
        AccountingFixtures.EUR_100);
  }
}
