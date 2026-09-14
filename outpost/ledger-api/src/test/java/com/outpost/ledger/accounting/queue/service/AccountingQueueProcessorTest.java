package com.outpost.ledger.accounting.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.accounting.transactionlock.TransactionLock;
import com.outpost.accounting.transactionlock.repository.TransactionLockRepository;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.queue.QueueItemResults;
import com.outpost.ledger.payment.service.AuthorisationService;
import com.outpost.ledger.payment.service.BookingErrorCodes;
import com.outpost.ledger.payment.service.BookingException;
import com.outpost.ledger.payment.service.CaptureService;
import com.outpost.ledger.payment.service.PaymentService;
import com.outpost.ledger.payment.service.RefundService;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AccountingQueueProcessorTest {
  private static final Instant LOCKED_AT = Instant.parse("2026-09-13T10:00:00Z");
  private static final TransactionLock LOCK =
      new TransactionLock("order-1", LOCKED_AT, LOCKED_AT.plusSeconds(300));

  private final PaymentService paymentService = mock(PaymentService.class);
  private final AuthorisationService authorisationService = mock(AuthorisationService.class);
  private final CaptureService captureService = mock(CaptureService.class);
  private final RefundService refundService = mock(RefundService.class);
  private final TransactionLockRepository transactionLocks = mock(TransactionLockRepository.class);
  private final AccountingQueueProcessor processor =
      new AccountingQueueProcessor(
          paymentService, authorisationService, captureService, refundService, transactionLocks);

  @ParameterizedTest
  @EnumSource(AccountingQueueRequestTypes.class)
  void routesEachRequestTypeToItsBooking(AccountingQueueRequestTypes type) {
    AccountingQueueRequest request = request(type, true);

    QueueItemResults result = processor.process(new LockedAccountingQueueRequest(request, LOCK));

    assertThat(result).isEqualTo(QueueItemResults.DONE);
    Object booking =
        switch (type) {
          case ORDER_CREATED -> {
            verify(paymentService).bookPayment(request);
            yield paymentService;
          }
          case AUTHORISATION -> {
            verify(authorisationService).bookAuthorisation("order-1", true);
            yield authorisationService;
          }
          case CAPTURE -> {
            verify(captureService).bookCapture("order-1", true);
            yield captureService;
          }
          case REFUND -> {
            verify(refundService).bookRefund("order-1", "refund-1");
            yield refundService;
          }
        };
    for (Object service :
        List.of(paymentService, authorisationService, captureService, refundService)) {
      if (service != booking) {
        verifyNoInteractions(service);
      }
    }
    verify(transactionLocks).deleteTransactionLock(LOCK);
  }

  @Test
  void booksNothingForFailedRefund() {
    AccountingQueueRequest request = request(AccountingQueueRequestTypes.REFUND, false);

    QueueItemResults result = processor.process(new LockedAccountingQueueRequest(request, LOCK));

    assertThat(result).isEqualTo(QueueItemResults.DONE);
    verifyNoInteractions(refundService, paymentService, authorisationService, captureService);
    verify(transactionLocks).deleteTransactionLock(LOCK);
  }

  @Test
  void releasesTheTransactionLockAfterBookingFails() {
    doThrow(new BookingException(BookingErrorCodes.INVALID_CAPTURE, null))
        .when(captureService)
        .bookCapture(any(), anyBoolean());
    AccountingQueueRequest request = request(AccountingQueueRequestTypes.CAPTURE, true);

    QueueItemResults result = processor.process(new LockedAccountingQueueRequest(request, LOCK));

    assertThat(result).isEqualTo(QueueItemResults.DONE);
    verify(transactionLocks).deleteTransactionLock(LOCK);
  }

  @Test
  void answersDoneWhenReleasingTheLockFails() {
    doThrow(new IllegalStateException("database unavailable"))
        .when(transactionLocks)
        .deleteTransactionLock(LOCK);
    AccountingQueueRequest request = request(AccountingQueueRequestTypes.AUTHORISATION, true);

    QueueItemResults result = processor.process(new LockedAccountingQueueRequest(request, LOCK));

    assertThat(result).isEqualTo(QueueItemResults.DONE);
    verify(authorisationService).bookAuthorisation("order-1", true);
    verify(captureService, never()).bookCapture(any(), anyBoolean());
  }

  private static AccountingQueueRequest request(
      AccountingQueueRequestTypes type, @Nullable Boolean success) {
    boolean orderCreated = type == AccountingQueueRequestTypes.ORDER_CREATED;
    return new AccountingQueueRequest(
        type,
        "order-1",
        "merchant-order-1",
        "DEMO_PSP",
        "41",
        orderCreated ? null : success,
        type == AccountingQueueRequestTypes.REFUND ? "refund-1" : null,
        orderCreated ? "DEMO_MERCHANT" : null,
        orderCreated ? Countries.GERMANY.getValue() : null,
        null,
        orderCreated ? eur(10_000) : null,
        orderCreated ? eur(2_000) : null,
        orderCreated ? eur(12_000) : null);
  }

  private static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }
}
