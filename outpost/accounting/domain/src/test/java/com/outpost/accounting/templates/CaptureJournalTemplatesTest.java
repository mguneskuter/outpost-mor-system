package com.outpost.accounting.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.AccountingFixtures;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.CaptureRegisters;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.PendingFee;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptureJournalTemplatesTest {
  private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");
  private static final Amount GROSS = new Amount(Currencies.EUR.getValue(), 12_000L);
  private static final Amount NET = new Amount(Currencies.EUR.getValue(), 10_000L);
  private static final Amount TAX = new Amount(Currencies.EUR.getValue(), 2_000L);
  private static final Amount FEE = new Amount(Currencies.EUR.getValue(), 500L);

  private final CaptureRegisters registers =
      new CaptureRegisters(
          register(11L, AccountingFixtures.psp(), RegisterTypes.PSP_RECEIVABLE.getValue()),
          register(
              12L, account(AccountTypes.TAX_AUTHORITY, 400L), RegisterTypes.TAX_PAYABLE.getValue()),
          register(13L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue()),
          register(14L, AccountingFixtures.platform(), RegisterTypes.FEE_REVENUE.getValue()));
  private final PendingFee pendingFee =
      new PendingFee(
          FEE,
          register(15L, AccountingFixtures.merchant(), RegisterTypes.PENDING_FEE.getValue()),
          register(16L, AccountingFixtures.platform(), RegisterTypes.PENDING_FEE.getValue()));

  @Test
  void capturePostsExactlySixBalancedLines() {
    Transaction payment = payment();

    JournalEntry entry =
        CaptureJournalTemplates.CAPTURE.build(
            captured(payment, TransactionEventTypes.CAPTURED),
            paymentDetail(payment, TAX),
            registers,
            pendingFee,
            WHEN);

    assertEquals(JournalEntryTypes.CAPTURE.getValue(), entry.getJournalEntryType());
    assertEquals(
        List.of(12_000L, -2_000L, -9_500L, -500L, -500L, 500L),
        entry.getJournalEntryLines().stream().map(line -> line.getAmount().quantity()).toList());
    assertEquals(
        0L, entry.getJournalEntryLines().stream().mapToLong(l -> l.getAmount().quantity()).sum());
  }

  @Test
  void captureRejectsChildWithInvalidSplitOrWrongSource() {
    Transaction payment = payment();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            CaptureJournalTemplates.CAPTURE.build(
                captured(payment, TransactionEventTypes.CAPTURE_FAILED),
                paymentDetail(payment, new Amount(Currencies.EUR.getValue(), 1_999L)),
                registers,
                pendingFee,
                WHEN));
  }

  private static Transaction payment() {
    return Transaction.of(
        1L,
        TransactionTypes.PAYMENT.getValue(),
        AccountingFixtures.merchant(),
        "payment-1",
        GROSS,
        WHEN);
  }

  private static TransactionEvent captured(Transaction payment, TransactionEventTypes eventType) {
    Transaction capture =
        Transaction.childOf(
            payment,
            2L,
            TransactionTypes.CAPTURE.getValue(),
            AccountingFixtures.merchant(),
            "capture-2",
            GROSS,
            WHEN);
    return new TransactionEvent(3L, capture, eventType.getValue(), WHEN);
  }

  private static PaymentDetail paymentDetail(Transaction payment, Amount tax) {
    return new PaymentDetail(
        payment, Countries.GERMANY.getValue(), null, AccountingFixtures.psp(), NET, tax);
  }

  private static Register register(long id, Account account, RegisterTypes.RegisterType type) {
    return new Register(id, account, type);
  }

  private static Account account(AccountTypes type, long id) {
    Account root =
        Account.of(id - 1, AccountTypes.ROOT.getValue(), "ROOT-" + id, "Root", true, WHEN, null);
    return Account.of(
        id, type.getValue(), type.getValue().getCode() + "-" + id, "Account", true, WHEN, root);
  }
}
