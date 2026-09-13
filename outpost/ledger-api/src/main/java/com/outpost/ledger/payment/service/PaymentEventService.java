package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentProcessorStateMachine;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.ledger.payment.repository.ExistingPayment;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PaymentTransaction;
import com.outpost.ledger.payment.repository.PendingFee;
import com.outpost.payment.common.Amount;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Books PSP authorisation outcomes on a payment. */
public class PaymentEventService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(PaymentEventService.class));
  private final PaymentRepository repository;
  private final JournalEntryRepository journalEntryRepository;
  private final PaymentProcessorStateMachine stateMachine;

  /** Creates a service using the persistence seams and payment processor state machine. */
  public PaymentEventService(
      PaymentRepository repository,
      JournalEntryRepository journalEntryRepository,
      PaymentProcessorStateMachine stateMachine) {
    this.repository = repository;
    this.journalEntryRepository = journalEntryRepository;
    this.stateMachine = stateMachine;
  }

  /**
   * Books a PSP authorisation outcome: AUTHORISED when {@code success}, otherwise REFUSED and the
   * release of the pending fee. A repeat of a booked outcome writes nothing.
   *
   * @throws PaymentEventException 404 PAYMENT_NOT_FOUND, 409 INVALID_TRANSITION
   */
  @Transactional
  public void recordAuthorisation(String originalReference, boolean success) {
    TransactionEventType candidate =
        success
            ? TransactionEventTypes.AUTHORISED.getValue()
            : TransactionEventTypes.REFUSED.getValue();
    PaymentTransaction payment = repository.findPaymentTransactionForUpdate(originalReference);
    if (payment == null) {
      throw notFound();
    }
    List<PaymentEvent> existingEvents = repository.findPaymentEvents(payment.transactionId());
    if (existingEvents.stream()
        .anyMatch(
            event -> event.transactionEventTypeId() == candidate.getTransactionEventTypeId())) {
      LOGGER.info(
          "Authorisation outcome already booked",
          new StructuredLogField(LogField.ORIGINAL_REFERENCE, originalReference),
          new StructuredLogField(LogField.EVENT, candidate.getCode()));
      return;
    }
    TransactionEventType current = paymentFold(existingEvents);
    if (!stateMachine.isNext(
        current, candidate, repository.findCaptureChild(payment.transactionId()) != null)) {
      throw new PaymentEventException(409, "INVALID_TRANSITION");
    }

    PaymentEvent event =
        repository.insertPaymentEvent(
            payment.transactionId(), candidate.getTransactionEventTypeId());
    if (event == null) {
      return;
    }
    if (releasesPendingFee(candidate)) {
      appendFeeRelease(payment, originalReference, candidate, event);
    }
    LOGGER.info(
        success ? "Payment authorised" : "Payment refused and its pending fee released",
        new StructuredLogField(LogField.ORIGINAL_REFERENCE, originalReference),
        new StructuredLogField(LogField.EVENT, candidate.getCode()));
  }

  private void appendFeeRelease(
      PaymentTransaction payment,
      String originalReference,
      TransactionEventType candidate,
      PaymentEvent event) {
    try {
      PendingFee pendingFee = repository.findPendingFee(payment.transactionId());
      Account platformAccount = repository.findPlatformAccount();
      if (pendingFee == null || platformAccount == null) {
        throw new IllegalArgumentException("Pending fee or platform account is missing");
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
              event.transactionEventId(),
              buildTransaction(payment, originalReference),
              candidate,
              event.occurredAt());
      JournalEntry release =
          PendingFeeJournalTemplates.FEE_RELEASE.build(
              releaseEvent,
              merchantPending,
              platformPending,
              new Amount(currency(pendingFee.currencyId()), pendingFee.fee()),
              event.occurredAt());
      journalEntryRepository.insertJournalEntry(release);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new PaymentEventException(500, "INTERNAL_ERROR");
    }
  }

  private Transaction buildTransaction(PaymentTransaction payment, String originalReference) {
    Account merchant = repository.findAccountById(payment.merchantAccountId());
    ExistingPayment created = repository.findByReference(originalReference);
    if (merchant == null || created == null || created.transactionId() != payment.transactionId()) {
      throw new IllegalArgumentException("Payment transaction is missing");
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
      throw new IllegalArgumentException("Register is missing or differs from the posted one");
    }
    return register;
  }

  private TransactionEventType paymentFold(List<PaymentEvent> existingEvents) {
    try {
      return stateMachine.fold(
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
        .orElseThrow(() -> new IllegalArgumentException("Unknown transaction event type"));
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

  private static PaymentEventException notFound() {
    return new PaymentEventException(404, "PAYMENT_NOT_FOUND");
  }

  private enum LogField implements LogFields {
    ORIGINAL_REFERENCE("original_reference"),
    EVENT("event");

    private final String jsonKey;

    LogField(String jsonKey) {
      this.jsonKey = jsonKey;
    }

    @Override
    public String getJsonKey() {
      return jsonKey;
    }
  }
}
