package com.outpost.ledger.payment.service;

import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.payment.PaymentLifecycle;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentFamily;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PendingFee;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** Records a Worker-authorised payment lifecycle event and its accounting evidence. */
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
    TransactionEventType candidate = candidate(request);
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

    TransactionEventType current = fold(recorded);
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

  private TransactionEventType fold(List<PaymentEvent> recorded) {
    try {
      return lifecycle.fold(recorded.stream().map(PaymentEventService::eventType).toList());
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

  private static TransactionEventType candidate(PaymentEventCommand request) {
    if (request == null
        || request.paymentReference() == null
        || request.paymentReference().isBlank()
        || request.event() == null
        || request.event().isBlank()) {
      throw bad();
    }
    TransactionEventType candidate =
        TransactionEventTypes.fromCode(request.event()).orElseThrow(PaymentEventService::bad);
    if (!releasesPendingFee(candidate)
        && !candidate.equals(TransactionEventTypes.AUTHORISED.getValue())) {
      throw bad();
    }
    return candidate;
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
}
