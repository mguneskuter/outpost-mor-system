package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.RefundDetail;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentLifecycle;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.accounting.templates.RefundJournalTemplates;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.repository.CapturePosting;
import com.outpost.ledger.payment.repository.ExistingPayment;
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

/** Appends a Worker-authorised payment or refund lifecycle event and its accounting evidence. */
public class PaymentEventService {
  private final PaymentRepository repository;
  private final JournalEntryRepository journalEntryRepository;
  private final PaymentLifecycle lifecycle;
  private final Clock clock;

  /** Creates a service using the ledger clock, persistence seams, and payment lifecycle. */
  public PaymentEventService(
      PaymentRepository repository,
      JournalEntryRepository journalEntryRepository,
      PaymentLifecycle lifecycle,
      Clock clock) {
    this.repository = repository;
    this.journalEntryRepository = journalEntryRepository;
    this.lifecycle = lifecycle;
    this.clock = clock;
  }

  /** Appends one valid lifecycle event, or returns without writing for an exact duplicate. */
  @Transactional
  public void appendPaymentEvent(AppendPaymentEventCommand request) {
    validateRequest(request);
    if (request.refundReference() != null) {
      appendToRefund(request);
      return;
    }
    appendToPayment(request);
  }

  private void appendToPayment(AppendPaymentEventCommand request) {
    TransactionEventType candidate = paymentCandidate(request.event());
    PaymentFamily payment = repository.findPaymentFamilyForUpdate(request.paymentReference());
    if (payment == null) {
      throw notFound();
    }
    List<PaymentEvent> existingEvents = repository.findPaymentEvents(payment.transactionId());
    if (existingEvents.stream()
        .anyMatch(
            event -> event.transactionEventTypeId() == candidate.getTransactionEventTypeId())) {
      return;
    }
    TransactionEventType current = paymentFold(existingEvents);
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
      appendFeeRelease(payment, request.paymentReference(), candidate, eventId, occurredAt);
    }
  }

  private void appendToRefund(AppendPaymentEventCommand request) {
    TransactionEventType candidate = refundCandidate(request.event());
    PaymentFamily payment = repository.findPaymentFamilyForUpdate(request.paymentReference());
    if (payment == null) {
      throw notFound();
    }
    RefundChild refund = repository.findRefundByReference(request.refundReference());
    if (refund == null || refund.paymentTransactionId() != payment.transactionId()) {
      throw refundNotFound();
    }
    List<PaymentEvent> existingEvents = repository.findPaymentEvents(refund.transactionId());
    if (existingEvents.stream()
        .anyMatch(
            event -> event.transactionEventTypeId() == candidate.getTransactionEventTypeId())) {
      return;
    }
    TransactionEventType current = refundFold(existingEvents);
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

  private void appendFeeRelease(
      PaymentFamily payment,
      String paymentReference,
      TransactionEventType candidate,
      long eventId,
      Instant occurredAt) {
    try {
      PendingFee pendingFee = repository.findPendingFee(payment.transactionId());
      Account platformAccount = repository.findPlatformAccount();
      if (pendingFee == null || platformAccount == null) {
        throw new IllegalArgumentException("pending fee or platform account is missing");
      }
      Register merchantPending =
          register(
              payment.merchantAccountId(),
              RegisterTypes.PENDING_FEE,
              pendingFee.merchantRegisterId());
      Register platformPending =
          register(
              platformAccount.getAccountId(),
              RegisterTypes.PENDING_FEE,
              pendingFee.platformRegisterId());
      TransactionEvent releaseEvent =
          new TransactionEvent(
              eventId, buildTransaction(payment, paymentReference), candidate, occurredAt);
      JournalEntry release =
          PendingFeeJournalTemplates.FEE_RELEASE.build(
              releaseEvent,
              merchantPending,
              platformPending,
              new Amount(currency(pendingFee.currencyId()), pendingFee.fee()),
              occurredAt);
      journalEntryRepository.insertJournalEntry(release);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new PaymentEventException(500, "INTERNAL_ERROR");
    }
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

      Transaction parentTransaction = buildTransaction(payment, paymentReference);
      Transaction refundTransaction =
          Transaction.childOf(
              parentTransaction,
              refund.transactionId(),
              TransactionTypes.REFUND.getValue(),
              parentTransaction.getMerchantAccount(),
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
              refundEvent,
              psp,
              tax,
              merchant,
              new Amount(currency, refund.quantity()),
              new Amount(currency, refund.netQuantity()),
              new Amount(currency, refund.taxQuantity()),
              occurredAt);
      journalEntryRepository.insertJournalEntry(refundEntry);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new PaymentEventException(500, "INTERNAL_ERROR");
    }
  }

  private Transaction buildTransaction(PaymentFamily payment, String paymentReference) {
    Account merchant = repository.findAccountById(payment.merchantAccountId());
    ExistingPayment created = repository.findByReference(paymentReference);
    if (merchant == null || created == null || created.transactionId() != payment.transactionId()) {
      throw new IllegalArgumentException("payment transaction is missing");
    }
    return Transaction.of(
        payment.transactionId(),
        TransactionTypes.PAYMENT.getValue(),
        merchant,
        created.reference(),
        new Amount(currency(payment.currencyId()), payment.grossQuantity()),
        created.createdTs());
  }

  private Register register(long accountId, RegisterTypes type, long expectedRegisterId) {
    Register register = repository.findRegister(accountId, type.getValue().getRegisterTypeId());
    if (register == null
        || register.getAccount().getAccountId() != accountId
        || register.getRegisterId() != expectedRegisterId) {
      throw new IllegalArgumentException("register is missing or differs from the posted one");
    }
    return register;
  }

  private TransactionEventType paymentFold(List<PaymentEvent> existingEvents) {
    try {
      return lifecycle.fold(existingEvents.stream().map(PaymentEventService::eventType).toList());
    } catch (IllegalArgumentException exception) {
      throw new PaymentEventException(500, "INTERNAL_ERROR");
    }
  }

  private TransactionEventType refundFold(List<PaymentEvent> existingEvents) {
    try {
      return lifecycle.foldRefund(
          existingEvents.stream().map(PaymentEventService::eventType).toList());
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

  private static void validateRequest(AppendPaymentEventCommand request) {
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
