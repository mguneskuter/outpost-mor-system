package com.outpost.ledger.payment.service;

import static com.outpost.ledger.payment.service.BookingFixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.RefundDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RefundServiceTest {
  private final BookingFixtures fixtures = new BookingFixtures();
  private final TransactionRepository transactions = mock(TransactionRepository.class);
  private final JournalEntryRepository journalEntries = mock(JournalEntryRepository.class);
  private final PaymentDetail payment = fixtures.payment();
  private final Transaction paymentTransaction = payment.getPaymentTransaction();
  private final RefundService service = new RefundService(transactions, journalEntries);

  @Test
  void rejectsRefundOfAnUncapturedPayment() {
    arrangePayment();

    BookingException failure =
        catchThrowableOfType(
            BookingException.class,
            () -> service.bookRefund(BookingFixtures.PAYMENT_REFERENCE, "refund-1"));

    assertThat(failure.code()).isEqualTo(BookingErrorCodes.NOT_CAPTURED);
    verify(transactions, never()).insertRefundDetail(any());
  }

  @Test
  void rejectsSecondRefundOfRefundedPayment() {
    arrangePayment();
    Transaction capture =
        fixtures.child(paymentTransaction, 20L, TransactionTypes.CAPTURE, "capture-1");
    when(transactions.findCaptureTransactionEventByPayment(paymentTransaction))
        .thenReturn(
            Optional.of(BookingFixtures.event(21L, capture, TransactionEventTypes.CAPTURED)));
    when(transactions.findRefundDetailsByPayment(paymentTransaction))
        .thenReturn(List.of(refund("refund-earlier")));

    BookingException failure =
        catchThrowableOfType(
            BookingException.class,
            () -> service.bookRefund(BookingFixtures.PAYMENT_REFERENCE, "refund-2"));

    assertThat(failure.code()).isEqualTo(BookingErrorCodes.ALREADY_REFUNDED);
    verify(transactions, never()).insertRefundDetail(any());
    verify(journalEntries, never()).insertJournalEntry(any());
  }

  @Test
  void writesNothingForRepeatOfBookedRefund() {
    arrangePayment();
    when(transactions.findRefundDetailByReference("refund-1"))
        .thenReturn(Optional.of(refund("refund-1")));

    service.bookRefund(BookingFixtures.PAYMENT_REFERENCE, "refund-1");

    verify(transactions, never()).insertRefundDetail(any());
    verify(transactions, never()).insertTransactionEvent(any(), any());
    verify(journalEntries, never()).insertJournalEntry(any());
  }

  private void arrangePayment() {
    when(transactions.findPaymentDetailByReferenceForUpdate(BookingFixtures.PAYMENT_REFERENCE))
        .thenReturn(Optional.of(payment));
  }

  private RefundDetail refund(String reference) {
    return new RefundDetail(
        fixtures.child(paymentTransaction, 30L, TransactionTypes.REFUND, reference),
        eur(10_000L),
        eur(2_000L));
  }
}
