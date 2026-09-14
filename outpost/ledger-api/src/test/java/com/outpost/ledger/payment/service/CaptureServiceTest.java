package com.outpost.ledger.payment.service;

import static com.outpost.ledger.payment.service.BookingFixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.account.AccountTypes;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.JournalEntryLine;
import com.outpost.accounting.journalentry.PendingFee;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentStateMachine;
import com.outpost.accounting.repository.RegisterRepository;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CaptureServiceTest {
  private final BookingFixtures fixtures = new BookingFixtures();
  private final TransactionRepository transactions = mock(TransactionRepository.class);
  private final JournalEntryRepository journalEntries = mock(JournalEntryRepository.class);
  private final AccountRepository accounts = mock(AccountRepository.class);
  private final RegisterRepository registers = mock(RegisterRepository.class);
  private final CaptureService service =
      new CaptureService(
          transactions, journalEntries, accounts, registers, new PaymentStateMachine());

  @Test
  void failedCaptureReleasesThePendingFeeBookedAtCreation() {
    arrangeAuthorisedPayment(TransactionEventTypes.CAPTURE_FAILED, pendingFee(eur(500L)));

    service.bookCapture(BookingFixtures.PAYMENT_REFERENCE, false);

    ArgumentCaptor<JournalEntry> stored = ArgumentCaptor.forClass(JournalEntry.class);
    verify(journalEntries).insertJournalEntry(stored.capture());
    assertThat(stored.getValue().getJournalEntryType())
        .isEqualTo(JournalEntryTypes.FEE_RELEASE.getValue());
    assertThat(stored.getValue().getJournalEntryLines())
        .extracting(JournalEntryLine::getRegister, line -> line.getAmount().quantity())
        .containsExactlyInAnyOrder(
            tuple(fixtures.merchantPendingFee, -500L), tuple(fixtures.platformPendingFee, 500L));
  }

  @Test
  void successfulCapturePostsTheCaptureEntry() {
    arrangeAuthorisedPayment(TransactionEventTypes.CAPTURED, pendingFee(eur(500L)));
    when(accounts.findTaxAuthorityAccountByCountryId(Countries.GERMANY.getValue().getCountryId()))
        .thenReturn(Optional.of(fixtures.taxAuthority));
    when(accounts.findAccountByAccountType(AccountTypes.PLATFORM.getValue()))
        .thenReturn(Optional.of(fixtures.platform));
    stubRegister(new Register(30003L, fixtures.psp, RegisterTypes.PSP_RECEIVABLE.getValue()));
    stubRegister(
        new Register(100604L, fixtures.taxAuthority, RegisterTypes.TAX_PAYABLE.getValue()));
    stubRegister(
        new Register(20001L, fixtures.merchant, RegisterTypes.MERCHANT_PAYABLE.getValue()));
    stubRegister(new Register(10005L, fixtures.platform, RegisterTypes.FEE_REVENUE.getValue()));

    service.bookCapture(BookingFixtures.PAYMENT_REFERENCE, true);

    ArgumentCaptor<JournalEntry> stored = ArgumentCaptor.forClass(JournalEntry.class);
    verify(journalEntries).insertJournalEntry(stored.capture());
    assertThat(stored.getValue().getJournalEntryType())
        .isEqualTo(JournalEntryTypes.CAPTURE.getValue());
  }

  @Test
  void rejectsPendingFeeInAnotherCurrencyBeforeJournalWrites() {
    arrangeAuthorisedPayment(
        TransactionEventTypes.CAPTURE_FAILED,
        pendingFee(new Amount(Currencies.USD.getValue(), 500L)));

    assertInconsistentBookingWithoutJournalWrites();
  }

  @Test
  void rejectsReleaseToAnotherMerchantsPendingFeeRegisterBeforeJournalWrites() {
    Register foreign =
        new Register(21008L, fixtures.otherMerchant, RegisterTypes.PENDING_FEE.getValue());
    arrangeAuthorisedPayment(
        TransactionEventTypes.CAPTURE_FAILED,
        new PendingFee(eur(500L), foreign, fixtures.platformPendingFee));

    assertInconsistentBookingWithoutJournalWrites();
  }

  private void assertInconsistentBookingWithoutJournalWrites() {
    BookingException failure =
        catchThrowableOfType(
            BookingException.class,
            () -> service.bookCapture(BookingFixtures.PAYMENT_REFERENCE, false));
    assertThat(failure.code()).isEqualTo(BookingErrorCodes.INCONSISTENT_BOOKING);
    verify(journalEntries, never()).insertJournalEntry(any());
  }

  private void arrangeAuthorisedPayment(TransactionEventTypes captureEventType, PendingFee fee) {
    PaymentDetail payment = fixtures.payment();
    Transaction paymentTransaction = payment.getPaymentTransaction();
    when(transactions.findPaymentDetailByReferenceForUpdate(BookingFixtures.PAYMENT_REFERENCE))
        .thenReturn(Optional.of(payment));
    when(transactions.findTransactionEvents(paymentTransaction))
        .thenReturn(
            List.of(
                BookingFixtures.event(1L, paymentTransaction, TransactionEventTypes.ORDER_CREATED),
                BookingFixtures.event(2L, paymentTransaction, TransactionEventTypes.AUTHORISED)));
    when(journalEntries.findPendingFeeByPayment(paymentTransaction)).thenReturn(Optional.of(fee));
    when(transactions.insertTransaction(any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    fixtures.child(
                        paymentTransaction,
                        20L,
                        TransactionTypes.CAPTURE,
                        invocation.<Transaction>getArgument(0).getReference())));
    when(transactions.insertTransactionEvent(any(), eq(captureEventType.getValue())))
        .thenAnswer(
            invocation ->
                Optional.of(
                    BookingFixtures.event(
                        21L, invocation.<Transaction>getArgument(0), captureEventType)));
  }

  private PendingFee pendingFee(Amount fee) {
    return new PendingFee(fee, fixtures.merchantPendingFee, fixtures.platformPendingFee);
  }

  private void stubRegister(Register register) {
    when(registers.findRegisterByAccountAndRegisterType(
            register.getAccount(), register.getRegisterType()))
        .thenReturn(Optional.of(register));
  }
}
