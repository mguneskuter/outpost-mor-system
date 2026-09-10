package com.outpost.accounting;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;

final class AccountingFixtures {
  static final Instant CREATED = Instant.parse("2026-02-01T00:00:00Z");
  static final Amount EUR_100 = new Amount(Currencies.EUR.getValue(), 100L);

  private AccountingFixtures() {}

  static Account merchant() {
    Account root =
        Account.of(100L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);
    return Account.of(101L, AccountTypes.MERCHANT.getValue(), "M", "Merchant", true, CREATED, root);
  }

  static Account psp() {
    Account root =
        Account.of(200L, AccountTypes.ROOT.getValue(), "ROOT2", "Root", true, CREATED, null);
    return Account.of(201L, AccountTypes.PSP.getValue(), "PSP", "PSP", true, CREATED, root);
  }

  static JournalEntry journalEntry(long journalEntryId) {
    TransactionEvent event =
        new TransactionEvent(
            journalEntryId,
            payment(journalEntryId),
            TransactionEventTypes.CAPTURED.getValue(),
            CREATED);
    return new JournalEntry(
        journalEntryId, event, JournalEntryTypes.CAPTURE.getValue(), CREATED, CREATED);
  }

  static Transaction payment(long transactionId) {
    return new Transaction(
        transactionId,
        TransactionTypes.PAYMENT.getValue(),
        merchant(),
        "payment-" + transactionId,
        EUR_100,
        CREATED);
  }

  static Transaction capture(long transactionId) {
    return new Transaction(
        transactionId,
        TransactionTypes.CAPTURE.getValue(),
        merchant(),
        "capture-" + transactionId,
        EUR_100,
        CREATED);
  }

  static Transaction refund(long transactionId) {
    return new Transaction(
        transactionId,
        TransactionTypes.REFUND.getValue(),
        merchant(),
        "refund-" + transactionId,
        EUR_100,
        CREATED);
  }
}
