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
import com.outpost.accounting.journalentry.CaptureRegisters;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.RefundDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

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
            () ->
                service.bookRefund(
                    BookingFixtures.PAYMENT_REFERENCE, "refund-1", eur(10_000L), eur(2_000L)));

    assertThat(failure.code()).isEqualTo(BookingErrorCodes.NOT_CAPTURED);
    verify(transactions, never()).insertRefundDetail(any());
  }

  @Test
  void booksPartialRefundWithTheRequestedNetAndTax() {
    arrangeCapturedPayment();
    arrangeBooking();

    service.bookRefund(BookingFixtures.PAYMENT_REFERENCE, "refund-1", eur(4_000L), eur(800L));

    ArgumentCaptor<RefundDetail> booked = ArgumentCaptor.forClass(RefundDetail.class);
    verify(transactions).insertRefundDetail(booked.capture());
    assertThat(booked.getValue().getNetAmount()).isEqualTo(eur(4_000L));
    assertThat(booked.getValue().getTaxAmount()).isEqualTo(eur(800L));
    assertThat(booked.getValue().getRefundTransaction().getAmount()).isEqualTo(eur(4_800L));
    assertThat(booked.getValue().getRefundTransaction().getReference()).isEqualTo("refund-1");
    verify(journalEntries).insertJournalEntry(any());
  }

  @Test
  void booksSecondRefundWhoseSumsStayWithinThePayment() {
    arrangeCapturedPayment();
    arrangeBooking();
    when(transactions.findRefundDetailsByPayment(paymentTransaction))
        .thenReturn(List.of(refund("refund-earlier", 4_000L, 800L)));

    service.bookRefund(BookingFixtures.PAYMENT_REFERENCE, "refund-2", eur(6_000L), eur(1_200L));

    verify(transactions).insertRefundDetail(any());
    verify(journalEntries).insertJournalEntry(any());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refundsBeyondTheCapture")
  void refusesRefundThatWouldExceedTheCapturedPayment(
      String exceeded, Amount bookedNet, Amount bookedTax, Amount net, Amount tax) {
    arrangeCapturedPayment();
    when(transactions.findRefundDetailsByPayment(paymentTransaction))
        .thenReturn(List.of(refund("refund-earlier", bookedNet.quantity(), bookedTax.quantity())));

    BookingException failure =
        catchThrowableOfType(
            BookingException.class,
            () -> service.bookRefund(BookingFixtures.PAYMENT_REFERENCE, "refund-2", net, tax));

    assertThat(failure.code()).isEqualTo(BookingErrorCodes.REFUND_EXCEEDS_CAPTURE);
    verify(transactions, never()).insertRefundDetail(any());
    verify(transactions, never()).insertTransactionEvent(any(), any());
    verify(journalEntries, never()).insertJournalEntry(any());
  }

  /**
   * The payment is EUR 100.00 net, 20.00 tax, and 120.00 gross; gross is net plus tax on every
   * refund, so exceeding it always exceeds net or tax first.
   */
  static List<Arguments> refundsBeyondTheCapture() {
    return List.of(
        Arguments.of("net", eur(4_000L), eur(800L), eur(6_001L), eur(1_199L)),
        Arguments.of("tax", eur(4_000L), eur(800L), eur(5_999L), eur(1_201L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refundsInAnotherCurrency")
  void rejectsRefundInAnotherCurrencyThanThePayment(String scope, Amount net, Amount tax) {
    arrangeCapturedPayment();

    BookingException failure =
        catchThrowableOfType(
            BookingException.class,
            () -> service.bookRefund(BookingFixtures.PAYMENT_REFERENCE, "refund-1", net, tax));

    assertThat(failure.code()).isEqualTo(BookingErrorCodes.INVALID_REQUEST);
    verify(transactions, never()).insertRefundDetail(any());
    verify(journalEntries, never()).insertJournalEntry(any());
  }

  /** The payment is in EUR; the same quantities in USD are refused whatever they cover. */
  static List<Arguments> refundsInAnotherCurrency() {
    return List.of(
        Arguments.of("the whole payment", usd(10_000L), usd(2_000L)),
        Arguments.of("part of the payment", usd(4_000L), usd(800L)));
  }

  private static Amount usd(long quantity) {
    return new Amount(Currencies.USD.getValue(), quantity);
  }

  @Test
  void writesNothingForRepeatOfBookedRefundWithEqualAmounts() {
    arrangePayment();
    when(transactions.findRefundDetailByReference("refund-1"))
        .thenReturn(Optional.of(refund("refund-1", 4_000L, 800L)));

    service.bookRefund(BookingFixtures.PAYMENT_REFERENCE, "refund-1", eur(4_000L), eur(800L));

    verify(transactions, never()).insertRefundDetail(any());
    verify(transactions, never()).insertTransactionEvent(any(), any());
    verify(journalEntries, never()).insertJournalEntry(any());
  }

  @Test
  void rejectsRepeatOfBookedRefundWithDifferentAmounts() {
    arrangePayment();
    when(transactions.findRefundDetailByReference("refund-1"))
        .thenReturn(Optional.of(refund("refund-1", 4_000L, 800L)));

    BookingException failure =
        catchThrowableOfType(
            BookingException.class,
            () ->
                service.bookRefund(
                    BookingFixtures.PAYMENT_REFERENCE, "refund-1", eur(5_000L), eur(1_000L)));

    assertThat(failure.code()).isEqualTo(BookingErrorCodes.REFERENCE_CONFLICT);
    verify(transactions, never()).insertRefundDetail(any());
  }

  private void arrangePayment() {
    when(transactions.findPaymentDetailByReferenceForUpdate(BookingFixtures.PAYMENT_REFERENCE))
        .thenReturn(Optional.of(payment));
  }

  private void arrangeCapturedPayment() {
    arrangePayment();
    Transaction capture =
        fixtures.child(paymentTransaction, 20L, TransactionTypes.CAPTURE, "capture-1");
    when(transactions.findCaptureTransactionEventByPayment(paymentTransaction))
        .thenReturn(
            Optional.of(BookingFixtures.event(21L, capture, TransactionEventTypes.CAPTURED)));
  }

  /** Every write answers with the stored form of what it was given. */
  private void arrangeBooking() {
    when(transactions.insertRefundDetail(any()))
        .thenAnswer(
            invocation -> {
              RefundDetail requested = invocation.getArgument(0);
              Transaction stored =
                  fixtures.child(
                      paymentTransaction,
                      30L,
                      TransactionTypes.REFUND,
                      requested.getRefundTransaction().getReference(),
                      requested.getRefundTransaction().getAmount());
              return Optional.of(
                  new RefundDetail(stored, requested.getNetAmount(), requested.getTaxAmount()));
            });
    when(transactions.insertTransactionEvent(any(), any()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    BookingFixtures.event(
                        31L, invocation.getArgument(0), TransactionEventTypes.REFUNDED)));
    when(journalEntries.findCaptureRegistersByPayment(paymentTransaction))
        .thenReturn(
            Optional.of(
                new CaptureRegisters(
                    fixtures.pspReceivable,
                    fixtures.taxPayable,
                    fixtures.merchantPayable,
                    fixtures.feeRevenue)));
  }

  private RefundDetail refund(String reference, long net, long tax) {
    return new RefundDetail(
        fixtures.child(paymentTransaction, 30L, TransactionTypes.REFUND, reference, eur(net + tax)),
        eur(net),
        eur(tax));
  }
}
