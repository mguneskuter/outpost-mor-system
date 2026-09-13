package com.outpost.ledger.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.common.iso.Currencies;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PaymentTransaction;
import com.outpost.ledger.payment.repository.RefundChild;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
  private static final long EUR = Currencies.EUR.getValue().getCurrencyId();
  private static final long PAYMENT_ID = 10L;
  private static final long REFUNDED =
      TransactionEventTypes.REFUNDED.getValue().getTransactionEventTypeId();

  private final PaymentRepository repository = mock(PaymentRepository.class);
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final PaymentTransaction payment =
      new PaymentTransaction(PAYMENT_ID, EUR, 200L, 300L, 6L, 12_000L, 10_000L, 2_000L);
  private final RefundService service = new RefundService(repository, journalEntryRepository);

  @Test
  void rejectsRefundOfAnUncapturedPayment() {
    when(repository.findPaymentTransactionForUpdate("payment-1")).thenReturn(payment);
    when(repository.hasExactlyOneSuccessfulFullCapture(PAYMENT_ID, 12_000L, EUR)).thenReturn(false);

    RefundException failure =
        catchThrowableOfType(RefundException.class, () -> service.refund("payment-1", "refund-1"));

    assertThat(failure.status()).isEqualTo(422);
    assertThat(failure.code()).isEqualTo("NOT_CAPTURED");
    verify(repository, never())
        .insertRefundTransaction(anyLong(), anyLong(), any(), anyLong(), anyLong());
  }

  @Test
  void rejectsSecondRefundOfRefundedPayment() {
    when(repository.findPaymentTransactionForUpdate("payment-1")).thenReturn(payment);
    when(repository.hasExactlyOneSuccessfulFullCapture(PAYMENT_ID, 12_000L, EUR)).thenReturn(true);
    when(repository.findRefundChildren(PAYMENT_ID))
        .thenReturn(List.of(refundChild(20L, "refund-earlier", REFUNDED)));

    RefundException failure =
        catchThrowableOfType(RefundException.class, () -> service.refund("payment-1", "refund-2"));

    assertThat(failure.status()).isEqualTo(422);
    assertThat(failure.code()).isEqualTo("ALREADY_REFUNDED");
    verify(repository, never())
        .insertRefundTransaction(anyLong(), anyLong(), any(), anyLong(), anyLong());
    verify(journalEntryRepository, never()).insertJournalEntry(any());
  }

  @Test
  void writesNothingForRepeatOfBookedRefund() {
    when(repository.findPaymentTransactionForUpdate("payment-1")).thenReturn(payment);
    when(repository.findRefundByReference("refund-1"))
        .thenReturn(refundChild(20L, "refund-1", REFUNDED));

    service.refund("payment-1", "refund-1");

    verify(repository, never())
        .insertRefundTransaction(anyLong(), anyLong(), any(), anyLong(), anyLong());
    verify(repository, never()).insertPaymentEvent(anyLong(), anyLong());
    verify(journalEntryRepository, never()).insertJournalEntry(any());
  }

  private static RefundChild refundChild(long transactionId, String reference, long eventTypeId) {
    return new RefundChild(
        transactionId, PAYMENT_ID, reference, 12_000L, EUR, 10_000L, 2_000L, NOW, eventTypeId);
  }
}
