package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptureJournalTemplatesTest {
  private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");
  private static final Amount GROSS = new Amount(Currencies.EUR.getValue(), 12_000L);
  private static final Amount NET = new Amount(Currencies.EUR.getValue(), 10_000L);
  private static final Amount TAX = new Amount(Currencies.EUR.getValue(), 2_000L);
  private static final Amount FEE = new Amount(Currencies.EUR.getValue(), 500L);

  @Test
  void capturePostsExactlySixBalancedLines() {
    Transaction payment = AccountingFixtures.payment(1L);
    Transaction capture =
        new Transaction(
            2L,
            TransactionTypes.CAPTURE.getValue(),
            AccountingFixtures.merchant(),
            "capture-2",
            GROSS,
            WHEN);
    payment.attachChild(capture);
    TransactionEvent captured =
        new TransactionEvent(3L, capture, TransactionEventTypes.CAPTURED.getValue(), WHEN);
    Register psp = register(11L, AccountingFixtures.psp(), RegisterTypes.PSP_RECEIVABLE.getValue());
    Register tax =
        register(
            12L, account(AccountTypes.TAX_AUTHORITY, 400L), RegisterTypes.TAX_PAYABLE.getValue());
    Register merchantPayable =
        register(13L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue());
    Register feeRevenue =
        register(14L, AccountingFixtures.platform(), RegisterTypes.FEE_REVENUE.getValue());
    Register merchantPending =
        register(15L, AccountingFixtures.merchant(), RegisterTypes.PENDING_FEE.getValue());
    Register platformPending =
        register(16L, AccountingFixtures.platform(), RegisterTypes.PENDING_FEE.getValue());

    JournalEntry entry =
        CaptureJournalTemplates.CAPTURE.build(
            4L,
            41L,
            42L,
            43L,
            44L,
            45L,
            46L,
            captured,
            psp,
            tax,
            merchantPayable,
            feeRevenue,
            merchantPending,
            platformPending,
            GROSS,
            NET,
            TAX,
            FEE,
            WHEN);

    assertEquals(JournalEntryTypes.CAPTURE.getValue(), entry.getJournalEntryType());
    assertEquals(
        List.of(12_000L, -2_000L, -9_500L, -500L, -500L, 500L),
        entry.getJournalEntryLines().stream()
            .sorted(Comparator.comparingLong(JournalEntryLine::getJournalEntryLineId))
            .map(line -> line.getAmount().quantity())
            .toList());
    assertEquals(
        0L, entry.getJournalEntryLines().stream().mapToLong(l -> l.getAmount().quantity()).sum());
  }

  @Test
  void captureRejectsChildWithInvalidSplitOrWrongSource() {
    Transaction capture = AccountingFixtures.capture(10L);
    TransactionEvent wrongEvent =
        new TransactionEvent(11L, capture, TransactionEventTypes.CAPTURE_FAILED.getValue(), WHEN);
    Register psp = register(21L, AccountingFixtures.psp(), RegisterTypes.PSP_RECEIVABLE.getValue());
    Register tax =
        register(
            22L, account(AccountTypes.TAX_AUTHORITY, 401L), RegisterTypes.TAX_PAYABLE.getValue());
    Register merchantPayable =
        register(23L, AccountingFixtures.merchant(), RegisterTypes.MERCHANT_PAYABLE.getValue());
    Register feeRevenue =
        register(24L, AccountingFixtures.platform(), RegisterTypes.FEE_REVENUE.getValue());
    Register merchantPending =
        register(25L, AccountingFixtures.merchant(), RegisterTypes.PENDING_FEE.getValue());
    Register platformPending =
        register(26L, AccountingFixtures.platform(), RegisterTypes.PENDING_FEE.getValue());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            CaptureJournalTemplates.CAPTURE.build(
                12L,
                121L,
                122L,
                123L,
                124L,
                125L,
                126L,
                wrongEvent,
                psp,
                tax,
                merchantPayable,
                feeRevenue,
                merchantPending,
                platformPending,
                GROSS,
                NET,
                new Amount(Currencies.EUR.getValue(), 1_999L),
                FEE,
                WHEN));
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
