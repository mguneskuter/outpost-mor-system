package com.outpost.ledger.payment.service;

import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.RefundDetail;
import com.outpost.accounting.RefundJournalTemplates;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.payment.PaymentLifecycle;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.repository.CapturePosting;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentFamily;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PendingFee;
import com.outpost.ledger.payment.repository.RefundChild;
import com.outpost.payment.common.Amount;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** Records a Worker-authorised payment or refund lifecycle event and its accounting evidence. */
public class PaymentEventService {
  private final PaymentRepository repository;
  private final Clock clock;
  private final PaymentLifecycle lifecycle = new PaymentLifecycle();

  /** Creates a service using the ledger clock and persistence seam. */
  public PaymentEventService(PaymentRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /** Records one valid lifecycle event, or returns without writing for an exact duplicate. */
  @Transactional
  public void record(PaymentEventCommand request) {
    validateRequest(request);
    if (request.refundReference() != null) {
      recordRefund(request);
      return;
    }
    recordPayment(request);
  }

  private void recordPayment(PaymentEventCommand request) {
    TransactionEventType candidate = paymentCandidate(request.event());
    PaymentFamily payment = repository.findPaymentFamilyForUpdate(request.paymentReference());
    if (payment == null) {
      throw notFound();
    }
    List<PaymentEvent> recorded = repository.findPaymentEvents(payment.transactionId());
    if (recorded.stream()
        .anyMatch(
            event -> event.transactionEventTypeId() == candidate.getTransactionEventTypeId())) {
      return;
    }
    TransactionEventType current = paymentFold(recorded);
    if (!lifecycle.canFollow(
        current, candidate, repository.findCaptureChild(payment.transactionId()) != null)) {
      throw new PaymentEventException(409, "INVALID_TRANSITION");
    }

    Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    Long eventId =
        repository.insertPaymentEvent(
            payment.transactionId(), candidate.getTransactionEventTypeId(), occurredAt);
    if (eventId == null) {
      return;
    }
    if (releasesPendingFee(candidate)) {
      appendFeeRelease(payment, eventId, occurredAt);
    }
  }

  private void recordRefund(PaymentEventCommand request) {
    TransactionEventType candidate = refundCandidate(request.event());
    PaymentFamily payment = repository.findPaymentFamilyForUpdate(request.paymentReference());
    if (payment == null) {
      throw notFound();
    }
    RefundChild refund = repository.findRefundByReference(request.refundReference());
    if (refund == null || refund.paymentTransactionId() != payment.transactionId()) {
      throw refundNotFound();
    }
    List<PaymentEvent> recorded = repository.findPaymentEvents(refund.transactionId());
    if (recorded.stream()
        .anyMatch(
            event -> event.transactionEventTypeId() == candidate.getTransactionEventTypeId())) {
      return;
    }
    TransactionEventType current = refundFold(recorded);
    if (!lifecycle.canFollowRefund(current, candidate)) {
      throw new PaymentEventException(409, "INVALID_TRANSITION");
    }

    Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    Long eventId =
        repository.insertPaymentEvent(
            refund.transactionId(), candidate.getTransactionEventTypeId(), occurredAt);
    if (eventId == null) {
      return;
    }
    if (candidate.equals(TransactionEventTypes.REFUNDED.getValue())) {
      appendRefundEntry(payment, request.paymentReference(), refund, eventId, occurredAt);
    }
  }

  private void appendFeeRelease(PaymentFamily payment, long eventId, Instant occurredAt) {
    PendingFee pendingFee = repository.findPendingFee(payment.transactionId());
    if (pendingFee == null
        || pendingFee.fee() < 0
        || pendingFee.currencyId() != payment.currencyId()) {
      throw new PaymentEventException(500, "INTERNAL_ERROR");
    }
    long entryId =
        repository.insertFeeReleaseEntry(
            eventId, JournalEntryTypes.FEE_RELEASE.getValue().getJournalEntryTypeId(), occurredAt);
    repository.insertLine(
        entryId, pendingFee.merchantRegisterId(), pendingFee.currencyId(), -pendingFee.fee());
    repository.insertLine(
        entryId, pendingFee.platformRegisterId(), pendingFee.currencyId(), pendingFee.fee());
  }

  private void appendRefundEntry(
      PaymentFamily payment,
      String paymentReference,
      RefundChild refund,
      long eventId,
      Instant occurredAt) {
    try {
      CapturePosting posting = repository.findCapturePosting(payment.transactionId());
      Currency currency = currency(payment.currencyId());
      if (posting == null
          || posting.currencyId() != payment.currencyId()
          || refund.currencyId() != payment.currencyId()
          || refund.quantity() != Math.addExact(refund.netQuantity(), refund.taxQuantity())) {
        throw new IllegalArgumentException("capture posting is not compatible with refund");
      }
      Register psp =
          register(posting.pspAccountId(), RegisterTypes.PSP_RECEIVABLE, posting.pspRegisterId());
      Register tax =
          register(
              posting.taxAuthorityAccountId(), RegisterTypes.TAX_PAYABLE, posting.taxRegisterId());
      Register merchant =
          register(
              posting.merchantAccountId(),
              RegisterTypes.MERCHANT_PAYABLE,
              posting.merchantRegisterId());
      if (merchant.getAccount().getAccountId() != payment.merchantAccountId()) {
        throw new IllegalArgumentException("capture merchant differs from payment merchant");
      }

      Transaction paymentTransaction =
          Transaction.of(
              payment.transactionId(),
              TransactionTypes.PAYMENT.getValue(),
              merchant.getAccount(),
              paymentReference,
              new Amount(currency, payment.grossQuantity()),
              refund.createdTs());
      Transaction refundTransaction =
          Transaction.childOf(
              paymentTransaction,
              refund.transactionId(),
              TransactionTypes.REFUND.getValue(),
              merchant.getAccount(),
              refund.reference(),
              new Amount(currency, refund.quantity()),
              refund.createdTs());
      new RefundDetail(
          refundTransaction,
          new Amount(currency, refund.netQuantity()),
          new Amount(currency, refund.taxQuantity()));
      TransactionEvent refundEvent =
          new TransactionEvent(
              eventId, refundTransaction, TransactionEventTypes.REFUNDED.getValue(), occurredAt);
      JournalEntry refundEntry =
          RefundJournalTemplates.REFUND.build(
              1L,
              1L,
              2L,
              3L,
              refundEvent,
              psp,
              tax,
              merchant,
              new Amount(currency, refund.quantity()),
              new Amount(currency, refund.netQuantity()),
              new Amount(currency, refund.taxQuantity()),
              occurredAt);
      long entryId =
          repository.insertRefundEntry(
              eventId, JournalEntryTypes.REFUND.getValue().getJournalEntryTypeId(), occurredAt);
      for (JournalEntryLine line : refundEntry.getJournalEntryLines()) {
        repository.insertLine(
            entryId,
            line.getRegister().getRegisterId(),
            line.getAmount().currency().getCurrencyId(),
            line.getAmount().quantity());
      }
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new PaymentEventException(500, "INTERNAL_ERROR");
    }
  }

  private Register register(long accountId, RegisterTypes type, long expectedRegisterId) {
    Register register = repository.findRegister(accountId, type.getValue().getRegisterTypeId());
    if (register == null || register.getRegisterId() != expectedRegisterId) {
      throw new IllegalArgumentException("capture register is missing or changed");
    }
    return register;
  }

  private TransactionEventType paymentFold(List<PaymentEvent> recorded) {
    try {
      return lifecycle.fold(recorded.stream().map(PaymentEventService::eventType).toList());
    } catch (IllegalArgumentException exception) {
      throw new PaymentEventException(500, "INTERNAL_ERROR");
    }
  }

  private TransactionEventType refundFold(List<PaymentEvent> recorded) {
    try {
      return lifecycle.foldRefund(recorded.stream().map(PaymentEventService::eventType).toList());
    } catch (IllegalArgumentException exception) {
      throw new PaymentEventException(500, "INTERNAL_ERROR");
    }
  }

  private static TransactionEventType eventType(PaymentEvent event) {
    return Arrays.stream(TransactionEventTypes.values())
        .map(TransactionEventTypes::getValue)
        .filter(type -> type.getTransactionEventTypeId() == event.transactionEventTypeId())
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown transaction event type"));
  }

  private static TransactionEventType paymentCandidate(String event) {
    TransactionEventType candidate =
        TransactionEventTypes.fromCode(event).orElseThrow(PaymentEventService::bad);
    if (!releasesPendingFee(candidate)
        && !candidate.equals(TransactionEventTypes.AUTHORISED.getValue())) {
      throw bad();
    }
    return candidate;
  }

  private static TransactionEventType refundCandidate(String event) {
    TransactionEventType candidate =
        TransactionEventTypes.fromCode(event).orElseThrow(PaymentEventService::bad);
    if (!candidate.equals(TransactionEventTypes.REFUND_ACCEPTED.getValue())
        && !candidate.equals(TransactionEventTypes.REFUNDED.getValue())
        && !candidate.equals(TransactionEventTypes.REFUND_FAILED.getValue())) {
      throw bad();
    }
    return candidate;
  }

  private static void validateRequest(PaymentEventCommand request) {
    if (request == null
        || request.paymentReference() == null
        || request.paymentReference().isBlank()
        || request.event() == null
        || request.event().isBlank()
        || (request.refundReference() != null && request.refundReference().isBlank())) {
      throw bad();
    }
  }

  private static Currency currency(long currencyId) {
    return Arrays.stream(Currencies.values())
        .map(Currencies::getValue)
        .filter(value -> value.getCurrencyId() == currencyId)
        .findFirst()
        .orElseThrow(IllegalArgumentException::new);
  }

  private static boolean releasesPendingFee(TransactionEventType eventType) {
    return eventType.equals(TransactionEventTypes.REFUSED.getValue())
        || eventType.equals(TransactionEventTypes.CANCELLED.getValue());
  }

  private static PaymentEventException bad() {
    return new PaymentEventException(400, "INVALID_REQUEST");
  }

  private static PaymentEventException notFound() {
    return new PaymentEventException(404, "PAYMENT_NOT_FOUND");
  }

  private static PaymentEventException refundNotFound() {
    return new PaymentEventException(404, "REFUND_NOT_FOUND");
  }
}
