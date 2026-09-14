package com.outpost.accounting.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.AccountingFixtures;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PendingFeeJournalTemplatesTest {
  private static final Instant WHEN = Instant.parse("2026-03-01T00:00:00Z");
  private static final Amount FEE = new Amount(Currencies.EUR.getValue(), 12L);

  @Test
  void feePendingBuildsBalancedTwoLineEntryOnOrderCreated() {
    Transaction payment = AccountingFixtures.payment(1L);
    TransactionEvent orderCreated =
        new TransactionEvent(1L, payment, TransactionEventTypes.ORDER_CREATED.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(1L, payment.getMerchantAccount());
    Register platformRegister = pendingFeeRegister(2L, AccountingFixtures.platform());

    JournalEntry entry =
        PendingFeeJournalTemplates.FEE_PENDING.build(
            orderCreated, merchantRegister, platformRegister, FEE, WHEN);

    assertEquals(JournalEntryTypes.FEE_PENDING.getValue(), entry.getJournalEntryType());
    assertEquals(2, entry.getJournalEntryLines().size());
    long balance =
        entry.getJournalEntryLines().stream().mapToLong(line -> line.getAmount().quantity()).sum();
    assertEquals(0L, balance);
    assertTrue(
        entry.getJournalEntryLines().stream()
            .anyMatch(
                line ->
                    line.getRegister().equals(merchantRegister)
                        && line.getAmount().quantity() == 12L));
    assertTrue(
        entry.getJournalEntryLines().stream()
            .anyMatch(
                line ->
                    line.getRegister().equals(platformRegister)
                        && line.getAmount().quantity() == -12L));
  }

  @Test
  void feePendingBuildsBalancedEntryForZeroFee() {
    Transaction payment = AccountingFixtures.payment(2L);
    TransactionEvent orderCreated =
        new TransactionEvent(2L, payment, TransactionEventTypes.ORDER_CREATED.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(3L, payment.getMerchantAccount());
    Register platformRegister = pendingFeeRegister(4L, AccountingFixtures.platform());

    JournalEntry entry =
        PendingFeeJournalTemplates.FEE_PENDING.build(
            orderCreated,
            merchantRegister,
            platformRegister,
            new Amount(Currencies.EUR.getValue(), 0L),
            WHEN);

    assertEquals(2, entry.getJournalEntryLines().size());
  }

  @ParameterizedTest
  @EnumSource(
      value = TransactionEventTypes.class,
      names = {"REFUSED", "CANCELLED"})
  void feeReleaseBooksTheOppositeLinesOnEachAllowedEvent(TransactionEventTypes eventType) {
    Transaction payment = AccountingFixtures.payment(100L);
    TransactionEvent releaseEvent = new TransactionEvent(101L, payment, eventType.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(102L, payment.getMerchantAccount());
    Register platformRegister = pendingFeeRegister(103L, AccountingFixtures.platform());

    JournalEntry entry =
        PendingFeeJournalTemplates.FEE_RELEASE.build(
            releaseEvent, merchantRegister, platformRegister, FEE, WHEN);

    assertEquals(JournalEntryTypes.FEE_RELEASE.getValue(), entry.getJournalEntryType());
    assertTrue(
        entry.getJournalEntryLines().stream()
            .anyMatch(
                line ->
                    line.getRegister().equals(merchantRegister)
                        && line.getAmount().quantity() == -12L));
    assertTrue(
        entry.getJournalEntryLines().stream()
            .anyMatch(
                line ->
                    line.getRegister().equals(platformRegister)
                        && line.getAmount().quantity() == 12L));
  }

  @Test
  void feeReleaseBooksTheOppositeLinesOnCaptureFailedFromCaptureTransaction() {
    Transaction capture = AccountingFixtures.capture(200L);
    TransactionEvent captureFailed =
        new TransactionEvent(201L, capture, TransactionEventTypes.CAPTURE_FAILED.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(202L, capture.getMerchantAccount());
    Register platformRegister = pendingFeeRegister(203L, AccountingFixtures.platform());

    JournalEntry entry =
        PendingFeeJournalTemplates.FEE_RELEASE.build(
            captureFailed, merchantRegister, platformRegister, FEE, WHEN);

    assertEquals(JournalEntryTypes.FEE_RELEASE.getValue(), entry.getJournalEntryType());
    assertTrue(
        entry.getJournalEntryLines().stream()
            .anyMatch(
                line ->
                    line.getRegister().equals(merchantRegister)
                        && line.getAmount().quantity() == -12L));
    assertTrue(
        entry.getJournalEntryLines().stream()
            .anyMatch(
                line ->
                    line.getRegister().equals(platformRegister)
                        && line.getAmount().quantity() == 12L));
  }

  @Test
  void rejectsCaptureFailedOnPaymentTransaction() {
    Transaction payment = AccountingFixtures.payment(210L);
    TransactionEvent captureFailed =
        new TransactionEvent(211L, payment, TransactionEventTypes.CAPTURE_FAILED.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(212L, payment.getMerchantAccount());
    Register platformRegister = pendingFeeRegister(213L, AccountingFixtures.platform());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PendingFeeJournalTemplates.FEE_RELEASE.build(
                captureFailed, merchantRegister, platformRegister, FEE, WHEN));
  }

  @Test
  void rejectsOrderCreatedOnCaptureTransaction() {
    Transaction capture = AccountingFixtures.capture(220L);
    TransactionEvent orderCreated =
        new TransactionEvent(221L, capture, TransactionEventTypes.ORDER_CREATED.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(222L, capture.getMerchantAccount());
    Register platformRegister = pendingFeeRegister(223L, AccountingFixtures.platform());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PendingFeeJournalTemplates.FEE_PENDING.build(
                orderCreated, merchantRegister, platformRegister, FEE, WHEN));
  }

  @Test
  void rejectsFeeCurrencyDifferentFromTheSourceTransactionCurrency() {
    Transaction payment = AccountingFixtures.payment(230L);
    TransactionEvent orderCreated =
        new TransactionEvent(231L, payment, TransactionEventTypes.ORDER_CREATED.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(232L, payment.getMerchantAccount());
    Register platformRegister = pendingFeeRegister(233L, AccountingFixtures.platform());
    Amount wrongCurrencyFee = new Amount(Currencies.USD.getValue(), 12L);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PendingFeeJournalTemplates.FEE_PENDING.build(
                orderCreated, merchantRegister, platformRegister, wrongCurrencyFee, WHEN));
  }

  @Test
  void rejectsSourceEventOfTheWrongType() {
    Transaction payment = AccountingFixtures.payment(3L);
    TransactionEvent authorised =
        new TransactionEvent(5L, payment, TransactionEventTypes.AUTHORISED.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(5L, payment.getMerchantAccount());
    Register platformRegister = pendingFeeRegister(6L, AccountingFixtures.platform());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PendingFeeJournalTemplates.FEE_PENDING.build(
                authorised, merchantRegister, platformRegister, FEE, WHEN));
  }

  @Test
  void rejectsMerchantRegisterOfTheWrongRegisterType() {
    Transaction payment = AccountingFixtures.payment(4L);
    TransactionEvent orderCreated =
        new TransactionEvent(6L, payment, TransactionEventTypes.ORDER_CREATED.getValue(), WHEN);
    Register wrongTypeRegister =
        new Register(7L, payment.getMerchantAccount(), RegisterTypes.MERCHANT_PAYABLE.getValue());
    Register platformRegister = pendingFeeRegister(8L, AccountingFixtures.platform());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PendingFeeJournalTemplates.FEE_PENDING.build(
                orderCreated, wrongTypeRegister, platformRegister, FEE, WHEN));
  }

  @Test
  void rejectsMerchantRegisterBelongingToAnotherAccount() {
    Transaction payment = AccountingFixtures.payment(5L);
    TransactionEvent orderCreated =
        new TransactionEvent(7L, payment, TransactionEventTypes.ORDER_CREATED.getValue(), WHEN);
    Account otherMerchantRoot =
        Account.of(600L, AccountTypes.ROOT.getValue(), "R600", "Root", true, WHEN, null);
    Account otherMerchant =
        Account.of(
            601L,
            AccountTypes.MERCHANT.getValue(),
            "M600",
            "Other merchant",
            true,
            WHEN,
            otherMerchantRoot);
    Register foreignMerchantRegister = pendingFeeRegister(9L, otherMerchant);
    Register platformRegister = pendingFeeRegister(10L, AccountingFixtures.platform());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PendingFeeJournalTemplates.FEE_PENDING.build(
                orderCreated, foreignMerchantRegister, platformRegister, FEE, WHEN));
  }

  @Test
  void rejectsPlatformRegisterOwnedByTheWrongAccountType() {
    Transaction payment = AccountingFixtures.payment(6L);
    TransactionEvent orderCreated =
        new TransactionEvent(8L, payment, TransactionEventTypes.ORDER_CREATED.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(11L, payment.getMerchantAccount());
    Register notPlatformRegister = pendingFeeRegister(12L, AccountingFixtures.psp());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PendingFeeJournalTemplates.FEE_PENDING.build(
                orderCreated, merchantRegister, notPlatformRegister, FEE, WHEN));
  }

  @Test
  void rejectsNegativeFee() {
    Transaction payment = AccountingFixtures.payment(7L);
    TransactionEvent orderCreated =
        new TransactionEvent(9L, payment, TransactionEventTypes.ORDER_CREATED.getValue(), WHEN);
    Register merchantRegister = pendingFeeRegister(13L, payment.getMerchantAccount());
    Register platformRegister = pendingFeeRegister(14L, AccountingFixtures.platform());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PendingFeeJournalTemplates.FEE_PENDING.build(
                orderCreated,
                merchantRegister,
                platformRegister,
                new Amount(Currencies.EUR.getValue(), -1L),
                WHEN));
  }

  private static Register pendingFeeRegister(long registerId, Account account) {
    return new Register(registerId, account, RegisterTypes.PENDING_FEE.getValue());
  }
}
