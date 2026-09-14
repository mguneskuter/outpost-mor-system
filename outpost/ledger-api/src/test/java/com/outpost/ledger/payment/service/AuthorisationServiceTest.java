package com.outpost.ledger.payment.service;

import static com.outpost.ledger.payment.service.BookingFixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.JournalEntryLine;
import com.outpost.accounting.journalentry.PendingFee;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentStateMachine;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthorisationServiceTest {
  private final BookingFixtures fixtures = new BookingFixtures();
  private final TransactionRepository transactions = mock(TransactionRepository.class);
  private final JournalEntryRepository journalEntries = mock(JournalEntryRepository.class);
  private final AuthorisationService service =
      new AuthorisationService(transactions, journalEntries, new PaymentStateMachine());

  @Test
  void releasesThePendingFeeOnRefusal() {
    arrangeRefusal(
        new PendingFee(eur(500L), fixtures.merchantPendingFee, fixtures.platformPendingFee));

    service.bookAuthorisation(BookingFixtures.PAYMENT_REFERENCE, false);

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
  void rejectsReleaseToNonPlatformPendingFeeRegisterBeforeJournalWrites() {
    Register notPlatform = new Register(30008L, fixtures.psp, RegisterTypes.PENDING_FEE.getValue());
    arrangeRefusal(new PendingFee(eur(500L), fixtures.merchantPendingFee, notPlatform));

    assertInconsistentBookingWithoutJournalWrites();
  }

  @Test
  void rejectsReleaseFromMerchantRegisterOfTheWrongTypeBeforeJournalWrites() {
    Register merchantPayable =
        new Register(20001L, fixtures.merchant, RegisterTypes.MERCHANT_PAYABLE.getValue());
    arrangeRefusal(new PendingFee(eur(500L), merchantPayable, fixtures.platformPendingFee));

    assertInconsistentBookingWithoutJournalWrites();
  }

  @Test
  void rejectsReleaseOfPendingFeeInAnotherCurrencyBeforeJournalWrites() {
    arrangeRefusal(
        new PendingFee(
            new Amount(Currencies.USD.getValue(), 500L),
            fixtures.merchantPendingFee,
            fixtures.platformPendingFee));

    assertInconsistentBookingWithoutJournalWrites();
  }

  private void assertInconsistentBookingWithoutJournalWrites() {
    BookingException failure =
        catchThrowableOfType(
            BookingException.class,
            () -> service.bookAuthorisation(BookingFixtures.PAYMENT_REFERENCE, false));
    assertThat(failure.code()).isEqualTo(BookingErrorCodes.INCONSISTENT_BOOKING);
    verify(journalEntries, never()).insertJournalEntry(any());
  }

  private void arrangeRefusal(PendingFee pendingFee) {
    PaymentDetail payment = fixtures.payment();
    Transaction paymentTransaction = payment.getPaymentTransaction();
    when(transactions.findPaymentDetailByReferenceForUpdate(BookingFixtures.PAYMENT_REFERENCE))
        .thenReturn(Optional.of(payment));
    when(transactions.findTransactionEvents(paymentTransaction))
        .thenReturn(
            List.of(
                BookingFixtures.event(
                    1L, paymentTransaction, TransactionEventTypes.ORDER_CREATED)));
    when(transactions.insertTransactionEvent(
            paymentTransaction, TransactionEventTypes.REFUSED.getValue()))
        .thenReturn(
            Optional.of(
                BookingFixtures.event(21L, paymentTransaction, TransactionEventTypes.REFUSED)));
    when(journalEntries.findPendingFeeByPayment(paymentTransaction))
        .thenReturn(Optional.of(pendingFee));
  }
}
