package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.JournalEntryLine;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import org.junit.jupiter.api.Test;

class AccountingEntityIdentityTest {
  @Test
  void registersDifferWhenAnyConfiguredFieldDiffers() {
    Register register =
        new Register(1L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue());
    assertEquals(
        register,
        new Register(1L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue()));
    assertEquals(
        register.hashCode(),
        new Register(1L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue())
            .hashCode());
    assertNotEquals(
        register,
        new Register(1L, AccountingFixtures.psp(), RegisterTypes.PSP_RECEIVABLE.getValue()));
    assertNotEquals(
        register,
        new Register(2L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue()));
    assertNotEquals(
        register,
        new Register(1L, AccountingFixtures.merchant(), RegisterTypes.PSP_RECEIVABLE.getValue()));
    assertNotEquals(null, register);
    assertNotEquals(new Object(), register);
  }

  @Test
  void transactionsDifferWhenAnyConfiguredFieldDiffers() {
    Transaction payment = AccountingFixtures.payment(3L);
    assertEquals(payment, AccountingFixtures.payment(3L));
    assertEquals(payment.hashCode(), AccountingFixtures.payment(3L).hashCode());
    assertNotEquals(
        payment,
        Transaction.of(
            4L,
            TransactionTypes.PAYMENT.getValue(),
            AccountingFixtures.merchant(),
            "payment-3",
            AccountingFixtures.EUR_100,
            AccountingFixtures.CREATED));
    assertNotEquals(
        payment,
        Transaction.of(
            3L,
            TransactionTypes.CAPTURE.getValue(),
            AccountingFixtures.merchant(),
            "payment-3",
            AccountingFixtures.EUR_100,
            AccountingFixtures.CREATED));
    assertNotEquals(
        payment,
        Transaction.of(
            3L,
            TransactionTypes.PAYMENT.getValue(),
            AccountingFixtures.psp(),
            "payment-3",
            AccountingFixtures.EUR_100,
            AccountingFixtures.CREATED));
    assertNotEquals(
        payment,
        Transaction.of(
            3L,
            TransactionTypes.PAYMENT.getValue(),
            AccountingFixtures.merchant(),
            "other-reference",
            AccountingFixtures.EUR_100,
            AccountingFixtures.CREATED));
    assertNotEquals(
        payment,
        Transaction.of(
            3L,
            TransactionTypes.PAYMENT.getValue(),
            AccountingFixtures.merchant(),
            "payment-3",
            new Amount(Currencies.EUR.getValue(), 200L),
            AccountingFixtures.CREATED));
    assertNotEquals(null, payment);
    assertNotEquals(new Object(), payment);
  }

  @Test
  void transactionsIgnoreCreatedAtBecauseItIsNotPartOfTheirIdentity() {
    Transaction payment = AccountingFixtures.payment(3L);
    Transaction withDifferentCreatedAt =
        Transaction.of(
            3L,
            TransactionTypes.PAYMENT.getValue(),
            AccountingFixtures.merchant(),
            "payment-3",
            AccountingFixtures.EUR_100,
            AccountingFixtures.CREATED.plusSeconds(1));

    assertEquals(payment, withDifferentCreatedAt);
    assertEquals(payment.hashCode(), withDifferentCreatedAt.hashCode());
  }

  @Test
  void rejectsNonPositiveEntityIds() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Register(
                0L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Transaction.of(
                0L,
                TransactionTypes.PAYMENT.getValue(),
                AccountingFixtures.merchant(),
                "payment",
                AccountingFixtures.EUR_100,
                AccountingFixtures.CREATED));
  }

  @Test
  void acceptsJournalEntryLinesWithZeroAmount() {
    JournalEntry entry = AccountingFixtures.journalEntry(1L);
    JournalEntryLine zeroLine =
        new JournalEntryLine(
            entry,
            new Register(
                2L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue()),
            new Amount(Currencies.EUR.getValue(), 0L));

    entry.addLine(zeroLine);
    assertEquals(0L, zeroLine.getAmount().quantity());
  }
}
