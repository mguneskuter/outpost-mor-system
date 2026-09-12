package com.outpost.ledger.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.common.iso.Currencies;
import com.outpost.ledger.payment.repository.PaymentFamily;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.RefundChild;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundReservationServiceTest {
  @Test
  void reservesUsingTheAccountReturnedByTheRepositoryContract() {
    PaymentRepository repository = mock(PaymentRepository.class);
    ReserveRefundCommand command =
        new ReserveRefundCommand("payment-1", "refund-1", 8000L, 2000L, "EUR");
    PaymentFamily payment =
        new PaymentFamily(
            1L, Currencies.EUR.getValue().getCurrencyId(), 200L, 300L, 1L, 12000L, 10000L, 2000L);
    Instant createdAt = Instant.parse("2026-09-12T00:00:00Z");
    Account root =
        Account.of(100L, AccountTypes.ROOT.getValue(), "OUTPOST", "Outpost", true, createdAt, null);
    Account merchant =
        Account.of(
            200L, AccountTypes.MERCHANT.getValue(), "MERCHANT", "Merchant", true, createdAt, root);
    when(repository.findPaymentFamilyForUpdate("payment-1")).thenReturn(payment);
    when(repository.findRefundByReference("refund-1")).thenReturn(null);
    when(repository.findByReference("refund-1")).thenReturn(null);
    long currencyId = Currencies.EUR.getValue().getCurrencyId();
    long requestedEventTypeId =
        TransactionEventTypes.REFUND_REQUESTED.getValue().getTransactionEventTypeId();
    when(repository.hasExactlyOneSuccessfulFullCapture(1L, 12000L, currencyId)).thenReturn(true);
    when(repository.findRefundChildren(1L)).thenReturn(List.of());
    when(repository.findAccountById(200L)).thenReturn(merchant);
    when(repository.insertRefundTransaction(1L, 200L, "refund-1", 10000L, currencyId, createdAt))
        .thenReturn(2L);
    when(repository.insertPaymentEvent(2L, requestedEventTypeId, createdAt)).thenReturn(3L);

    ReserveRefundResult result =
        new RefundReservationService(repository, Clock.fixed(createdAt, ZoneOffset.UTC))
            .reserve(command);

    assertThat(result).isEqualTo(new ReserveRefundResult("refund-1", createdAt));
    verify(repository).findAccountById(200L);
  }

  @Test
  void returnsApplicationResultForExactReplay() {
    PaymentRepository repository = mock(PaymentRepository.class);
    ReserveRefundCommand command =
        new ReserveRefundCommand("payment-1", "refund-1", 8000L, 2000L, "EUR");
    PaymentFamily payment =
        new PaymentFamily(
            1L, Currencies.EUR.getValue().getCurrencyId(), 200L, 300L, 1L, 12000L, 10000L, 2000L);
    Instant createdAt = Instant.parse("2026-09-12T00:00:00Z");
    RefundChild existing =
        new RefundChild(
            2L,
            1L,
            "refund-1",
            10000L,
            Currencies.EUR.getValue().getCurrencyId(),
            8000L,
            2000L,
            createdAt,
            TransactionEventTypes.REFUND_REQUESTED.getValue().getTransactionEventTypeId());
    when(repository.findPaymentFamilyForUpdate("payment-1")).thenReturn(payment);
    when(repository.findRefundByReference("refund-1")).thenReturn(existing);

    ReserveRefundResult result =
        new RefundReservationService(repository, Clock.systemUTC()).reserve(command);

    assertThat(result).isEqualTo(new ReserveRefundResult("refund-1", createdAt));
  }
}
