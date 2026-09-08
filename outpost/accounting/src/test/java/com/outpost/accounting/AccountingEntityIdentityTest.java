package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import org.junit.jupiter.api.Test;

class AccountingEntityIdentityTest {
  @Test
  void entitiesCompareByTheirPermanentIdentity() {
    Register register =
        new Register(1L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.value());
    assertEquals(
        register, new Register(1L, AccountingFixtures.psp(), RegisterTypes.PSP_RECEIVABLE.value()));
    assertNotEquals(
        register,
        new Register(2L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.value()));

    Transaction payment = AccountingFixtures.payment(3L);
    assertEquals(payment, AccountingFixtures.payment(3L));
    assertNotEquals(payment, AccountingFixtures.payment(4L));
  }

  @Test
  void entityIdentitiesMustBePositiveAndJournalLinesMayBeZero() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Register(
                0L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.value()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Transaction(
                0L,
                TransactionTypes.PAYMENT.value(),
                AccountingFixtures.merchant(),
                "payment",
                AccountingFixtures.EUR_100,
                AccountingFixtures.CREATED));
    JournalEntry entry = JournalEntryTest.entry(1L);
    JournalEntryLine zeroLine =
        new JournalEntryLine(
            1L,
            entry,
            new Register(2L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.value()),
            new Amount(Currencies.EUR.value(), 0L));

    entry.addLine(zeroLine);
    assertEquals(0L, zeroLine.amount().quantity());
  }
}
