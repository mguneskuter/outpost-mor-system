package com.outpost.ledger.payment.service;

import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.PendingFee;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentStateMachine;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import java.util.List;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Books the PSP's authorisation answer on a payment. */
public class AuthorisationService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(AuthorisationService.class));
  private final TransactionRepository transactions;
  private final JournalEntryRepository journalEntries;
  private final PaymentStateMachine stateMachine;

  /** Creates a service over the repositories it books through and the payment state machine. */
  public AuthorisationService(
      TransactionRepository transactions,
      JournalEntryRepository journalEntries,
      PaymentStateMachine stateMachine) {
    this.transactions = transactions;
    this.journalEntries = journalEntries;
    this.stateMachine = stateMachine;
  }

  /**
   * Books the PSP's authorisation answer: AUTHORISED when {@code success}, otherwise REFUSED and
   * the release of the pending fee. A repeat of a booked answer writes nothing.
   *
   * @throws BookingException when the request is not booked; its code says why
   */
  @Transactional
  public void bookAuthorisation(String originalReference, boolean success) {
    TransactionEventType eventType =
        success
            ? TransactionEventTypes.AUTHORISED.getValue()
            : TransactionEventTypes.REFUSED.getValue();
    Transaction payment =
        transactions
            .findPaymentDetailByReferenceForUpdate(originalReference)
            .orElseThrow(() -> new BookingException(BookingErrorCodes.PAYMENT_NOT_FOUND, null))
            .getPaymentTransaction();
    List<TransactionEventType> bookedEventTypes =
        transactions.findTransactionEvents(payment).stream()
            .map(TransactionEvent::getTransactionEventType)
            .toList();
    if (bookedEventTypes.contains(eventType)) {
      LOGGER.info(
          "Authorisation already booked",
          new StructuredLogField(LogFields.ORIGINAL_REFERENCE, originalReference),
          new StructuredLogField(LogFields.EVENT, eventType.getCode()));
      return;
    }
    if (!stateMachine.isNext(
        fold(bookedEventTypes),
        eventType,
        transactions.findCaptureTransactionEventByPayment(payment).isPresent())) {
      throw new BookingException(BookingErrorCodes.INVALID_TRANSITION, null);
    }
    Optional<TransactionEvent> event = transactions.insertTransactionEvent(payment, eventType);
    if (event.isEmpty()) {
      return;
    }
    if (releasesPendingFee(eventType)) {
      journalEntries.insertJournalEntry(feeRelease(event.get()));
    }
    LOGGER.info(
        success ? "Payment authorised" : "Payment refused and its pending fee released",
        new StructuredLogField(LogFields.ORIGINAL_REFERENCE, originalReference),
        new StructuredLogField(LogFields.EVENT, eventType.getCode()));
  }

  private JournalEntry feeRelease(TransactionEvent event) {
    PendingFee pendingFee =
        journalEntries
            .findPendingFeeByPayment(event.getTransaction())
            .orElseThrow(() -> new BookingException(BookingErrorCodes.INCONSISTENT_BOOKING, null));
    try {
      return PendingFeeJournalTemplates.FEE_RELEASE.build(
          event,
          pendingFee.merchantPendingFeeRegister(),
          pendingFee.platformPendingFeeRegister(),
          pendingFee.fee(),
          event.getOccurredAt());
    } catch (IllegalArgumentException exception) {
      throw new BookingException(BookingErrorCodes.INCONSISTENT_BOOKING, exception);
    }
  }

  private TransactionEventType fold(List<TransactionEventType> bookedEventTypes) {
    try {
      return stateMachine.fold(bookedEventTypes);
    } catch (IllegalArgumentException exception) {
      throw new BookingException(BookingErrorCodes.INCONSISTENT_BOOKING, exception);
    }
  }

  private static boolean releasesPendingFee(TransactionEventType eventType) {
    return eventType.equals(TransactionEventTypes.REFUSED.getValue())
        || eventType.equals(TransactionEventTypes.CANCELLED.getValue());
  }
}
