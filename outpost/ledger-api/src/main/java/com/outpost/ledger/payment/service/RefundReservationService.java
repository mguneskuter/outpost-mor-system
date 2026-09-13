package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.accounting.RefundDetail;
import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentFamily;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.RefundChild;
import com.outpost.ledger.payment.repository.StoredTransaction;
import com.outpost.payment.common.Amount;
import org.springframework.transaction.annotation.Transactional;

/** Reserves a refundable amount without creating an accounting entry. */
public class RefundReservationService {
  private final PaymentRepository repository;

  /** Creates a service using the persistence seam. */
  public RefundReservationService(PaymentRepository repository) {
    this.repository = repository;
  }

  /** Reserves a refund, or returns the existing result for an exact replay. */
  @Transactional
  public ReserveRefundResult reserve(ReserveRefundCommand command) {
    validate(command);
    Currency currency =
        Currencies.fromCurrencyCode(command.currency()).orElseThrow(RefundReservationService::bad);
    long gross = add(command.netAmount(), command.taxAmount());
    if (gross <= 0) {
      throw invalidRefund();
    }

    PaymentFamily payment = repository.findPaymentFamilyForUpdate(command.paymentReference());
    if (payment == null) {
      throw notFound();
    }

    RefundChild existing = repository.findRefundByReference(command.refundReference());
    if (existing != null) {
      if (!sameFingerprint(existing, payment, command, currency, gross)) {
        throw conflict();
      }
      return new ReserveRefundResult(existing.reference(), existing.createdTs());
    }
    if (repository.findByReference(command.refundReference()) != null) {
      throw conflict();
    }

    validatePayment(payment, currency);
    if (!repository.hasExactlyOneSuccessfulFullCapture(
        payment.transactionId(), payment.grossQuantity(), payment.currencyId())) {
      throw invalidRefund();
    }

    long requestedNet = 0;
    long requestedTax = 0;
    for (RefundChild child : repository.findRefundChildren(payment.transactionId())) {
      long eventTypeId = requireEventType(child);
      if (eventTypeId
              == TransactionEventTypes.REFUND_REQUESTED.getValue().getTransactionEventTypeId()
          || eventTypeId
              == TransactionEventTypes.REFUND_ACCEPTED.getValue().getTransactionEventTypeId()) {
        throw invalidRefund();
      }
      if (eventTypeId
          == TransactionEventTypes.REFUND_FAILED.getValue().getTransactionEventTypeId()) {
        continue;
      }
      if (eventTypeId != TransactionEventTypes.REFUNDED.getValue().getTransactionEventTypeId()) {
        throw internal();
      }
      requestedNet = add(requestedNet, child.netQuantity());
      requestedTax = add(requestedTax, child.taxQuantity());
    }

    long cumulativeNet = add(requestedNet, command.netAmount());
    long cumulativeTax = add(requestedTax, command.taxAmount());
    long cumulativeGross = add(add(requestedNet, requestedTax), gross);
    if (cumulativeNet > payment.netQuantity()
        || cumulativeTax > payment.taxQuantity()
        || cumulativeGross > payment.grossQuantity()) {
      throw invalidRefund();
    }

    StoredTransaction refund =
        repository.insertRefundTransaction(
            payment.transactionId(),
            payment.merchantAccountId(),
            command.refundReference(),
            gross,
            currency.getCurrencyId());
    if (refund == null) {
      throw conflict();
    }
    createDomainDetail(payment, command, currency, gross, refund);
    repository.insertRefundDetail(refund.transactionId(), command.netAmount(), command.taxAmount());
    PaymentEvent event =
        repository.insertPaymentEvent(
            refund.transactionId(),
            TransactionEventTypes.REFUND_REQUESTED.getValue().getTransactionEventTypeId());
    if (event == null) {
      throw internal();
    }
    return new ReserveRefundResult(command.refundReference(), refund.createdAt());
  }

  private void createDomainDetail(
      PaymentFamily payment,
      ReserveRefundCommand command,
      Currency currency,
      long gross,
      StoredTransaction refund) {
    try {
      Account merchant = repository.findAccountById(payment.merchantAccountId());
      if (merchant == null) {
        throw internal();
      }
      Transaction paymentTransaction =
          Transaction.of(
              payment.transactionId(),
              TransactionTypes.PAYMENT.getValue(),
              merchant,
              command.paymentReference(),
              new Amount(currency, payment.grossQuantity()),
              refund.createdAt());
      Transaction refundTransaction =
          Transaction.childOf(
              paymentTransaction,
              refund.transactionId(),
              TransactionTypes.REFUND.getValue(),
              merchant,
              command.refundReference(),
              new Amount(currency, gross),
              refund.createdAt());
      new RefundDetail(
          refundTransaction,
          new Amount(currency, command.netAmount()),
          new Amount(currency, command.taxAmount()));
    } catch (IllegalArgumentException | IllegalStateException exception) {
      throw internal();
    }
  }

  private static boolean sameFingerprint(
      RefundChild existing,
      PaymentFamily payment,
      ReserveRefundCommand command,
      Currency currency,
      long gross) {
    return existing.paymentTransactionId() == payment.transactionId()
        && existing.reference().equals(command.refundReference())
        && existing.netQuantity() == command.netAmount()
        && existing.taxQuantity() == command.taxAmount()
        && existing.quantity() == gross
        && existing.currencyId() == currency.getCurrencyId();
  }

  private static long requireEventType(RefundChild child) {
    if (child.eventTypeId() == null) {
      throw internal();
    }
    return child.eventTypeId();
  }

  private static void validatePayment(PaymentFamily payment, Currency currency) {
    if (payment.currencyId() != currency.getCurrencyId()
        || payment.grossQuantity() <= 0
        || payment.netQuantity() < 0
        || payment.taxQuantity() < 0
        || add(payment.netQuantity(), payment.taxQuantity()) != payment.grossQuantity()) {
      throw invalidRefund();
    }
  }

  private static void validate(ReserveRefundCommand command) {
    if (command == null
        || command.paymentReference() == null
        || command.paymentReference().isBlank()
        || command.refundReference() == null
        || command.refundReference().isBlank()
        || command.netAmount() == null
        || command.taxAmount() == null
        || command.netAmount() < 0
        || command.taxAmount() < 0
        || command.currency() == null
        || command.currency().isBlank()) {
      throw bad();
    }
  }

  private static long add(long first, long second) {
    try {
      return Math.addExact(first, second);
    } catch (ArithmeticException exception) {
      throw invalidRefund();
    }
  }

  private static ReserveRefundException bad() {
    return new ReserveRefundException(400, "INVALID_REQUEST");
  }

  private static ReserveRefundException notFound() {
    return new ReserveRefundException(404, "PAYMENT_NOT_FOUND");
  }

  private static ReserveRefundException conflict() {
    return new ReserveRefundException(409, "REFERENCE_CONFLICT");
  }

  private static ReserveRefundException invalidRefund() {
    return new ReserveRefundException(422, "INVALID_REFUND");
  }

  private static ReserveRefundException internal() {
    return new ReserveRefundException(500, "INTERNAL_ERROR");
  }
}
