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
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentLifecycle;
import com.outpost.common.iso.Currencies;
import com.outpost.ledger.payment.repository.ExistingPayment;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PaymentTransaction;
import com.outpost.ledger.payment.repository.PendingFee;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentEventServiceTest {
  private static final Instant CREATED = Instant.parse("2026-09-13T09:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
  private static final long EUR = Currencies.EUR.getValue().getCurrencyId();
  private static final long PENDING_FEE = RegisterTypes.PENDING_FEE.getValue().getRegisterTypeId();
  private static final long PAYMENT_ID = 10L;
  private static final long EVENT_ID = 21L;

  private final PaymentRepository repository = mock(PaymentRepository.class);
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final Account root =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);
  private final Account platform =
      Account.of(100L, AccountTypes.PLATFORM.getValue(), "OUTPOST", "Outpost", true, CREATED, root);
  private final Account merchant =
      Account.of(200L, AccountTypes.MERCHANT.getValue(), "SHOP", "Shop", true, CREATED, root);
  private final Account psp =
      Account.of(300L, AccountTypes.PSP.getValue(), "PSP", "PSP", true, CREATED, root);
  private final Register merchantPendingFee =
      new Register(20008L, merchant, RegisterTypes.PENDING_FEE.getValue());
  private final Register platformPendingFee =
      new Register(10008L, platform, RegisterTypes.PENDING_FEE.getValue());
  private final PaymentTransaction payment =
      new PaymentTransaction(
          PAYMENT_ID,
          EUR,
          merchant.getAccountId(),
          psp.getAccountId(),
          6L,
          12_000L,
          10_000L,
          2_000L);
  private final PaymentEventService service =
      new PaymentEventService(repository, journalEntryRepository, new PaymentLifecycle());

  @Test
  void releasesThePendingFeeOnRefusal() {
    arrangeRelease(pendingFee(merchantPendingFee, platformPendingFee, EUR));

    service.recordAuthorisation("payment-1", false);

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
  void rejectsReleaseToNonPlatformPendingFeeRegisterBeforeJournalWrites() {
    Register notPlatform = new Register(30008L, psp, RegisterTypes.PENDING_FEE.getValue());
    arrangeRelease(pendingFee(merchantPendingFee, notPlatform, EUR));
    when(repository.findRegister(platform.getAccountId(), PENDING_FEE)).thenReturn(notPlatform);

    assertInternalErrorWithoutJournalWrites();
  }

  @Test
  void rejectsReleaseFromMerchantRegisterOfTheWrongTypeBeforeJournalWrites() {
    Register merchantPayable =
        new Register(20001L, merchant, RegisterTypes.MERCHANT_PAYABLE.getValue());
    arrangeRelease(pendingFee(merchantPayable, platformPendingFee, EUR));
    when(repository.findRegister(merchant.getAccountId(), PENDING_FEE)).thenReturn(merchantPayable);

    assertInternalErrorWithoutJournalWrites();
  }

  @Test
  void rejectsReleaseOfPendingFeeInAnotherCurrencyBeforeJournalWrites() {
    arrangeRelease(
        pendingFee(
            merchantPendingFee, platformPendingFee, Currencies.USD.getValue().getCurrencyId()));

    assertInternalErrorWithoutJournalWrites();
  }

  private void assertInternalErrorWithoutJournalWrites() {
    PaymentEventException failure =
        catchThrowableOfType(
            PaymentEventException.class, () -> service.recordAuthorisation("payment-1", false));

    assertThat(failure.status()).isEqualTo(500);
    assertThat(failure.code()).isEqualTo("INTERNAL_ERROR");
    verify(journalEntryRepository, never()).insertJournalEntry(any());
  }

  private void arrangeRelease(PendingFee pendingFee) {
    TransactionEventTypes transactionEventType = TransactionEventTypes.REFUSED;
    when(repository.findPaymentTransactionForUpdate("payment-1")).thenReturn(payment);
    when(repository.findByReference("payment-1"))
        .thenReturn(
            new ExistingPayment(
                PAYMENT_ID,
                merchant.getAccountId(),
                "payment-1",
                12_000L,
                EUR,
                CREATED,
                psp.getAccountId(),
                6L,
                null,
                10_000L,
                2_000L));
    when(repository.findPaymentEvents(PAYMENT_ID))
        .thenReturn(List.of(event(1L, TransactionEventTypes.ORDER_CREATED)));
    when(repository.insertPaymentEvent(
            PAYMENT_ID, transactionEventType.getValue().getTransactionEventTypeId()))
        .thenReturn(
            new PaymentEvent(
                EVENT_ID, transactionEventType.getValue().getTransactionEventTypeId(), NOW));
    when(repository.findPendingFee(PAYMENT_ID)).thenReturn(pendingFee);
    when(repository.findAccountById(merchant.getAccountId())).thenReturn(merchant);
    when(repository.findPlatformAccount()).thenReturn(platform);
    when(repository.findRegister(merchant.getAccountId(), PENDING_FEE))
        .thenReturn(merchantPendingFee);
    when(repository.findRegister(platform.getAccountId(), PENDING_FEE))
        .thenReturn(platformPendingFee);
  }

  private static PaymentEvent event(long id, TransactionEventTypes type) {
    return new PaymentEvent(id, type.getValue().getTransactionEventTypeId(), CREATED);
  }

  private static PendingFee pendingFee(
      Register merchantRegister, Register platformRegister, long currencyId) {
    return new PendingFee(
        500L, currencyId, merchantRegister.getRegisterId(), platformRegister.getRegisterId());
  }
}
