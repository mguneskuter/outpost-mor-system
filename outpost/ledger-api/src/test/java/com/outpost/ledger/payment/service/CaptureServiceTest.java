package com.outpost.ledger.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.api.CaptureRequest;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.common.iso.Currencies;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentFamily;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PendingFee;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CaptureServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
  private static final long EUR = Currencies.EUR.getValue().getCurrencyId();
  private static final long PAYMENT_ID = 10L;
  private static final long CAPTURE_TRANSACTION_ID = 20L;
  private static final long EVENT_ID = 21L;

  private final PaymentRepository repository = mock(PaymentRepository.class);
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final Account root =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, NOW, null);
  private final Account platform =
      Account.of(100L, AccountTypes.PLATFORM.getValue(), "OUTPOST", "Outpost", true, NOW, root);
  private final Account merchant =
      Account.of(200L, AccountTypes.MERCHANT.getValue(), "SHOP", "Shop", true, NOW, root);
  private final Account otherMerchant =
      Account.of(210L, AccountTypes.MERCHANT.getValue(), "OTHER", "Other", true, NOW, root);
  private final Register merchantPendingFee =
      new Register(20008L, merchant, RegisterTypes.PENDING_FEE.getValue());
  private final Register platformPendingFee =
      new Register(10008L, platform, RegisterTypes.PENDING_FEE.getValue());
  private final PaymentFamily payment =
      new PaymentFamily(
          PAYMENT_ID, EUR, merchant.getAccountId(), 300L, 6L, 12_000L, 10_000L, 2_000L);
  private final CaptureService service =
      new CaptureService(repository, journalEntryRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void failedCaptureReleasesThePendingFeeBookedAtCreation() {
    arrangePaymentAwaitingCapture(
        TransactionEventTypes.CAPTURE_FAILED,
        new PendingFee(
            500L, EUR, merchantPendingFee.getRegisterId(), platformPendingFee.getRegisterId()));

    service.capture(request(false));

    ArgumentCaptor<JournalEntry> stored = ArgumentCaptor.forClass(JournalEntry.class);
    verify(journalEntryRepository).insertJournalEntry(stored.capture());
    assertThat(stored.getValue().getJournalEntryType())
        .isEqualTo(JournalEntryTypes.FEE_RELEASE.getValue());
    assertThat(stored.getValue().getJournalEntryLines())
        .extracting(JournalEntryLine::getRegister, line -> line.getAmount().quantity())
        .containsExactlyInAnyOrder(
            tuple(merchantPendingFee, -500L), tuple(platformPendingFee, 500L));
  }

  @Test
  void rejectsPendingFeeInAnotherCurrencyBeforeJournalWrites() {
    arrangePaymentAwaitingCapture(
        TransactionEventTypes.CAPTURE_FAILED,
        new PendingFee(
            500L,
            Currencies.USD.getValue().getCurrencyId(),
            merchantPendingFee.getRegisterId(),
            platformPendingFee.getRegisterId()));

    assertInternalErrorWithoutJournalWrites(request(false));
  }

  @Test
  void rejectsReleaseToAnotherMerchantsPendingFeeRegisterBeforeJournalWrites() {
    Register foreign = new Register(21008L, otherMerchant, RegisterTypes.PENDING_FEE.getValue());
    arrangePaymentAwaitingCapture(
        TransactionEventTypes.CAPTURE_FAILED,
        new PendingFee(500L, EUR, foreign.getRegisterId(), platformPendingFee.getRegisterId()));
    when(repository.findRegister(merchant.getAccountId(), pendingFeeTypeId())).thenReturn(foreign);

    assertInternalErrorWithoutJournalWrites(request(false));
  }

  @Test
  void rejectsCaptureBookingFeeRevenueToAnotherPlatformAccountBeforeJournalWrites() {
    final Account psp =
        Account.of(300L, AccountTypes.PSP.getValue(), "PSP", "PSP", true, NOW, root);
    final Account taxAuthority =
        Account.of(
            1006L, AccountTypes.TAX_AUTHORITY.getValue(), "TAX_DE", "Tax DE", true, NOW, root);
    final Account otherPlatform =
        Account.of(
            110L, AccountTypes.PLATFORM.getValue(), "OTHER_PLATFORM", "Other", true, NOW, root);
    arrangePaymentAwaitingCapture(
        TransactionEventTypes.CAPTURED,
        new PendingFee(
            500L, EUR, merchantPendingFee.getRegisterId(), platformPendingFee.getRegisterId()));
    when(repository.findTaxAuthorityAccountByCountryId(6L)).thenReturn(taxAuthority);
    stubRegister(new Register(30003L, psp, RegisterTypes.PSP_RECEIVABLE.getValue()));
    stubRegister(new Register(100604L, taxAuthority, RegisterTypes.TAX_PAYABLE.getValue()));
    stubRegister(new Register(20001L, merchant, RegisterTypes.MERCHANT_PAYABLE.getValue()));
    when(repository.findRegister(
            platform.getAccountId(), RegisterTypes.FEE_REVENUE.getValue().getRegisterTypeId()))
        .thenReturn(new Register(11005L, otherPlatform, RegisterTypes.FEE_REVENUE.getValue()));

    assertInternalErrorWithoutJournalWrites(request(true));
  }

  private void assertInternalErrorWithoutJournalWrites(CaptureRequest request) {
    CaptureException failure =
        catchThrowableOfType(CaptureException.class, () -> service.capture(request));

    assertThat(failure.status()).isEqualTo(500);
    assertThat(failure.code()).isEqualTo("INTERNAL_ERROR");
    verify(journalEntryRepository, never()).insertJournalEntry(any());
  }

  private void arrangePaymentAwaitingCapture(
      TransactionEventTypes transactionEventType, PendingFee pendingFee) {
    when(repository.findPaymentFamilyForUpdate("payment-1")).thenReturn(payment);
    when(repository.findPaymentEvents(PAYMENT_ID))
        .thenReturn(
            List.of(
                event(1L, TransactionEventTypes.ORDER_CREATED),
                event(2L, TransactionEventTypes.AUTHORISED)));
    when(repository.findPendingFee(PAYMENT_ID)).thenReturn(pendingFee);
    when(repository.insertCaptureTransaction(
            PAYMENT_ID, merchant.getAccountId(), "capture-1", 12_000L, EUR, NOW))
        .thenReturn(CAPTURE_TRANSACTION_ID);
    when(repository.insertPaymentEvent(
            CAPTURE_TRANSACTION_ID,
            transactionEventType.getValue().getTransactionEventTypeId(),
            NOW))
        .thenReturn(EVENT_ID);
    when(repository.findAccountById(merchant.getAccountId())).thenReturn(merchant);
    when(repository.findPlatformAccount()).thenReturn(platform);
    stubRegister(merchantPendingFee);
    stubRegister(platformPendingFee);
  }

  private void stubRegister(Register register) {
    when(repository.findRegister(
            register.getAccount().getAccountId(), register.getRegisterType().getRegisterTypeId()))
        .thenReturn(register);
  }

  private static long pendingFeeTypeId() {
    return RegisterTypes.PENDING_FEE.getValue().getRegisterTypeId();
  }

  private static PaymentEvent event(long id, TransactionEventTypes type) {
    return new PaymentEvent(id, type.getValue().getTransactionEventTypeId(), NOW);
  }

  private static CaptureRequest request(boolean success) {
    return new CaptureRequest("payment-1", "capture-1", success, 12_000L, "EUR");
  }
}
