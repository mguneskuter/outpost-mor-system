package com.outpost.accounting.templates;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.accounting.AccountingFixtures;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.JournalEntryLine;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JournalTemplateValidatorTest {
  private static final Instant WHEN = Instant.parse("2026-03-01T00:00:00Z");

  private final JournalTemplateValidator validator = new JournalTemplateValidator();

  @Test
  void rejectsLinesThatSumToZeroOverallButNotPerCurrency() {
    JournalEntry entry =
        entryWithLines(
            new Amount(Currencies.EUR.getValue(), 100L),
            new Amount(Currencies.USD.getValue(), -100L));

    assertThrows(IllegalArgumentException.class, () -> validator.requireBalanced(entry));
  }

  @Test
  void acceptsLinesThatSumToZeroInEveryCurrency() {
    JournalEntry entry =
        entryWithLines(
            new Amount(Currencies.EUR.getValue(), 100L),
            new Amount(Currencies.USD.getValue(), 40L),
            new Amount(Currencies.EUR.getValue(), -100L),
            new Amount(Currencies.USD.getValue(), -40L));

    assertDoesNotThrow(() -> validator.requireBalanced(entry));
  }

  private static JournalEntry entryWithLines(Amount... amounts) {
    Transaction payment = AccountingFixtures.payment(1L);
    TransactionEvent orderCreated =
        new TransactionEvent(1L, payment, TransactionEventTypes.ORDER_CREATED.getValue(), WHEN);
    JournalEntry entry =
        new JournalEntry(orderCreated, JournalEntryTypes.FEE_PENDING.getValue(), WHEN, WHEN);
    Register register =
        new Register(1L, payment.getMerchantAccount(), RegisterTypes.PENDING_FEE.getValue());
    for (Amount amount : amounts) {
      entry.addLine(new JournalEntryLine(entry, register, amount));
    }
    return entry;
  }
}
