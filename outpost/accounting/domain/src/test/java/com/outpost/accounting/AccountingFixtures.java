package com.outpost.accounting;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;

/** Accounts, transactions, and journal entries shared by accounting domain tests. */
public final class AccountingFixtures {
  public static final Instant CREATED = Instant.parse("2026-02-01T00:00:00Z");
  public static final Amount EUR_100 = new Amount(Currencies.EUR.getValue(), 100L);

  private AccountingFixtures() {}

  /** Returns an active MERCHANT account. */
  public static Account merchant() {
    Account root =
        Account.of(100L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);
    return Account.of(101L, AccountTypes.MERCHANT.getValue(), "M", "Merchant", true, CREATED, root);
  }

  /** Returns an active PSP account. */
  public static Account psp() {
    Account root =
        Account.of(200L, AccountTypes.ROOT.getValue(), "ROOT2", "Root", true, CREATED, null);
    return Account.of(201L, AccountTypes.PSP.getValue(), "PSP", "PSP", true, CREATED, root);
  }

  /** Returns an active PLATFORM account. */
  public static Account platform() {
    Account root =
        Account.of(300L, AccountTypes.ROOT.getValue(), "ROOT3", "Root", true, CREATED, null);
    return Account.of(
        301L, AccountTypes.PLATFORM.getValue(), "PLATFORM", "Platform", true, CREATED, root);
  }

  /** Returns a CAPTURE entry without lines for a CAPTURED event of a fixture payment. */
  public static JournalEntry journalEntry(long sourceId) {
    TransactionEvent event =
        new TransactionEvent(
            sourceId, payment(sourceId), TransactionEventTypes.CAPTURED.getValue(), CREATED);
    return new JournalEntry(event, JournalEntryTypes.CAPTURE.getValue(), CREATED, CREATED);
  }

  /** Returns a PAYMENT transaction owned by the fixture merchant account. */
  public static Transaction payment(long transactionId) {
    return Transaction.of(
        transactionId,
        TransactionTypes.PAYMENT.getValue(),
        merchant(),
        "payment-" + transactionId,
        EUR_100,
        CREATED);
  }

  /** Returns a CAPTURE transaction owned by the fixture merchant account. */
  public static Transaction capture(long transactionId) {
    return Transaction.of(
        transactionId,
        TransactionTypes.CAPTURE.getValue(),
        merchant(),
        "capture-" + transactionId,
        EUR_100,
        CREATED);
  }

  /** Returns a REFUND transaction owned by the fixture merchant account. */
  public static Transaction refund(long transactionId) {
    return Transaction.of(
        transactionId,
        TransactionTypes.REFUND.getValue(),
        merchant(),
        "refund-" + transactionId,
        EUR_100,
        CREATED);
  }
}
