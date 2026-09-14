package com.outpost.accounting.journalentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.accounting.AccountingFixtures;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JournalEntryTest {
  private static final Register REGISTER =
      new Register(1L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue());

  @Test
  void entryOwnsOnlyItsLinesAndExposesAnImmutableView() {
    JournalEntry entry = AccountingFixtures.journalEntry(1L);
    JournalEntryLine line = line(entry, AccountingFixtures.EUR_100);

    entry.addLine(line);

    assertEquals(1, entry.getJournalEntryLines().size());
    assertThrows(UnsupportedOperationException.class, () -> entry.getJournalEntryLines().add(line));
  }

  @Test
  void entryRejectsLinesOfAnotherEntryAndTheSameLineTwiceWithoutMutation() {
    JournalEntry entry = AccountingFixtures.journalEntry(1L);
    JournalEntryLine foreignLine =
        line(AccountingFixtures.journalEntry(2L), AccountingFixtures.EUR_100);
    JournalEntryLine lineOfAnEqualEntry =
        line(AccountingFixtures.journalEntry(1L), AccountingFixtures.EUR_100);
    final JournalEntryLine line = line(entry, AccountingFixtures.EUR_100);

    assertThrows(IllegalArgumentException.class, () -> entry.addLine(foreignLine));
    assertThrows(IllegalArgumentException.class, () -> entry.addLine(lineOfAnEqualEntry));
    assertTrue(entry.getJournalEntryLines().isEmpty());
    entry.addLine(line);
    assertThrows(IllegalArgumentException.class, () -> entry.addLine(line));
    assertEquals(1, entry.getJournalEntryLines().size());
  }

  @Test
  void entryKeepsTwoEqualLines() {
    JournalEntry entry = AccountingFixtures.journalEntry(1L);

    entry.addLine(line(entry, AccountingFixtures.EUR_100));
    entry.addLine(line(entry, AccountingFixtures.EUR_100));

    assertEquals(2, entry.getJournalEntryLines().size());
  }

  @Test
  void storedEntryCarriesItsIdsAndKeepsTheLinesInOrder() {
    JournalEntry entry = AccountingFixtures.journalEntry(1L);
    Amount credit = new Amount(Currencies.EUR.getValue(), -100L);
    entry.addLine(line(entry, AccountingFixtures.EUR_100));
    entry.addLine(line(entry, credit));

    JournalEntry stored = entry.withIds(7L, List.of(70L, 71L));

    assertEquals(Optional.of(7L), stored.getJournalEntryId());
    assertEquals(
        List.of(Optional.of(70L), Optional.of(71L)),
        stored.getJournalEntryLines().stream()
            .map(JournalEntryLine::getJournalEntryLineId)
            .toList());
    assertEquals(
        List.of(AccountingFixtures.EUR_100, credit),
        stored.getJournalEntryLines().stream().map(JournalEntryLine::getAmount).toList());
    assertTrue(entry.getJournalEntryId().isEmpty());
  }

  @Test
  void storedEntryTakesNoNewLinesAndIsNotStoredTwice() {
    JournalEntry entry = AccountingFixtures.journalEntry(1L);
    entry.addLine(line(entry, AccountingFixtures.EUR_100));

    JournalEntry stored = entry.withIds(7L, List.of(70L));

    assertThrows(
        IllegalStateException.class,
        () -> stored.addLine(line(stored, AccountingFixtures.EUR_100)));
    assertThrows(IllegalStateException.class, () -> stored.withIds(8L, List.of(80L)));
  }

  @Test
  void rejectsIdsThatAreNotPositiveOrDoNotMatchTheLines() {
    JournalEntry entry = AccountingFixtures.journalEntry(1L);
    entry.addLine(line(entry, AccountingFixtures.EUR_100));

    assertThrows(IllegalArgumentException.class, () -> entry.withIds(7L, List.of()));
    assertThrows(IllegalArgumentException.class, () -> entry.withIds(0L, List.of(70L)));
    assertThrows(IllegalArgumentException.class, () -> entry.withIds(7L, List.of(0L)));
  }

  @Test
  void entriesAndLinesAreEqualWhenEveryFieldMatches() {
    JournalEntry first = AccountingFixtures.journalEntry(1L);
    JournalEntry second = AccountingFixtures.journalEntry(1L);

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertEquals(line(first, AccountingFixtures.EUR_100), line(second, AccountingFixtures.EUR_100));
    assertEquals(
        line(first, AccountingFixtures.EUR_100).hashCode(),
        line(second, AccountingFixtures.EUR_100).hashCode());
    assertNotEquals(first, AccountingFixtures.journalEntry(2L));
    assertNotEquals(first.withIds(7L, List.of()), second.withIds(8L, List.of()));
  }

  private static JournalEntryLine line(JournalEntry entry, Amount amount) {
    return new JournalEntryLine(entry, REGISTER, amount);
  }
}
