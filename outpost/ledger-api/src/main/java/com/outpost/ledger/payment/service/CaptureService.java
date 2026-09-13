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
import com.outpost.accounting.templates.CaptureJournalTemplates;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.repository.CaptureChild;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PaymentTransaction;
import com.outpost.ledger.payment.repository.PendingFee;
import com.outpost.ledger.payment.repository.StoredTransaction;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Books one PSP capture outcome on an authorised payment and its accounting evidence. */
public class CaptureService {
  private final PaymentRepository repository;
  private final JournalEntryRepository journalEntryRepository;
  private final PaymentProcessorStateMachine stateMachine;

  /** Creates a service using the persistence seams and payment processor state machine. */
  public CaptureService(
      PaymentRepository repository,
      JournalEntryRepository journalEntryRepository,
      PaymentProcessorStateMachine stateMachine) {
    this.repository = repository;
    this.journalEntryRepository = journalEntryRepository;
    this.stateMachine = stateMachine;
  }

  /**
   * Books the PSP's capture outcome as the payment's single CAPTURE child for the payment's gross
   * amount: CAPTURED and the CAPTURE entry when {@code success}, otherwise CAPTURE_FAILED and the
   * release of the pending fee. A repeat of the booked outcome writes nothing.
   *
   * @throws CaptureException 404 PAYMENT_NOT_FOUND, 409 CAPTURE_CONFLICT or REFERENCE_CONFLICT, 422
   *     INVALID_CAPTURE
   */
  @Transactional
  public void capture(String originalReference, boolean success) {
    TransactionEventType candidate =
        success
            ? TransactionEventTypes.CAPTURED.getValue()
            : TransactionEventTypes.CAPTURE_FAILED.getValue();
    PaymentTransaction payment = repository.findPaymentTransactionForUpdate(originalReference);
    if (payment == null) {
      throw new CaptureException(404, "PAYMENT_NOT_FOUND");
    }
    CaptureChild existing = repository.findCaptureChild(payment.transactionId());
    if (existing != null) {
      if (existing.eventTypeId() != null
          && existing.eventTypeId() == candidate.getTransactionEventTypeId()) {
        return;
      }
      throw new CaptureException(409, "CAPTURE_CONFLICT");
    }
    List<PaymentEvent> events = repository.findPaymentEvents(payment.transactionId());
    TransactionEventType paymentState = fold(events);
    if (!stateMachine.isNextCapture(paymentState, false, candidate)) {
      throw new CaptureException(422, "INVALID_CAPTURE");
    }
    PendingFee pendingFee = repository.findPendingFee(payment.transactionId());
    if (!validPendingFee(payment, pendingFee)) {
      throw internal();
    }

    String captureReference = "capture-" + UUID.randomUUID();
    Currency currency = currency(payment.currencyId());
    StoredTransaction capture =
        repository.insertCaptureTransaction(
            payment.transactionId(),
            payment.merchantAccountId(),
            captureReference,
            payment.grossQuantity(),
            payment.currencyId());
    if (capture == null) {
      throw new CaptureException(409, "REFERENCE_CONFLICT");
    }
    PaymentEvent event =
        repository.insertPaymentEvent(
            capture.transactionId(), candidate.getTransactionEventTypeId());
    if (event == null) {
      throw internal();
    }
    Account merchantAccount = repository.findAccountById(payment.merchantAccountId());
    if (merchantAccount == null) {
      throw internal();
    }
    TransactionEvent transactionEvent;
    try {
      transactionEvent =
          new TransactionEvent(
              event.transactionEventId(),
              Transaction.of(
                  capture.transactionId(),
                  TransactionTypes.CAPTURE.getValue(),
                  merchantAccount,
                  captureReference,
                  new Amount(currency, payment.grossQuantity()),
                  capture.createdAt()),
              candidate,
              event.occurredAt());
    } catch (IllegalArgumentException exception) {
      throw internal();
    }
    journalEntryRepository.insertJournalEntry(journalEntry(payment, pendingFee, transactionEvent));
  }

  private JournalEntry journalEntry(
      PaymentTransaction payment, PendingFee pendingFee, TransactionEvent transactionEvent) {
    try {
      Account platformAccount = repository.findPlatformAccount();
      if (platformAccount == null) {
        throw internal();
      }
      Register merchantPendingFee =
          register(payment.merchantAccountId(), RegisterTypes.PENDING_FEE);
      Register platformPendingFee =
          register(platformAccount.getAccountId(), RegisterTypes.PENDING_FEE);
      if (merchantPendingFee.getRegisterId() != pendingFee.merchantRegisterId()
          || platformPendingFee.getRegisterId() != pendingFee.platformRegisterId()) {
        throw internal();
      }
      Amount gross = transactionEvent.getTransaction().getAmount();
      Amount fee = new Amount(gross.currency(), pendingFee.fee());
      Instant occurredAt = transactionEvent.getOccurredAt();
      if (!transactionEvent
          .getTransactionEventType()
          .equals(TransactionEventTypes.CAPTURED.getValue())) {
        return PendingFeeJournalTemplates.FEE_RELEASE.build(
            transactionEvent, merchantPendingFee, platformPendingFee, fee, occurredAt);
      }
      Account taxAuthorityAccount =
          repository.findTaxAuthorityAccountByCountryId(payment.shopperCountryId());
      if (taxAuthorityAccount == null) {
        throw internal();
      }
      return CaptureJournalTemplates.CAPTURE.build(
          transactionEvent,
          register(payment.pspAccountId(), RegisterTypes.PSP_RECEIVABLE),
          register(taxAuthorityAccount.getAccountId(), RegisterTypes.TAX_PAYABLE),
          register(payment.merchantAccountId(), RegisterTypes.MERCHANT_PAYABLE),
          register(platformAccount.getAccountId(), RegisterTypes.FEE_REVENUE),
          merchantPendingFee,
          platformPendingFee,
          gross,
          new Amount(gross.currency(), payment.netQuantity()),
          new Amount(gross.currency(), payment.taxQuantity()),
          fee,
          occurredAt);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw internal();
    }
  }

  private Register register(long accountId, RegisterTypes registerType) {
    Register register =
        repository.findRegister(accountId, registerType.getValue().getRegisterTypeId());
    if (register == null || register.getAccount().getAccountId() != accountId) {
      throw internal();
    }
    return register;
  }

  private static boolean validPendingFee(PaymentTransaction payment, PendingFee fee) {
    return fee != null
        && fee.fee() >= 0
        && fee.fee() <= payment.netQuantity()
        && fee.currencyId() == payment.currencyId();
  }

  private TransactionEventType fold(List<PaymentEvent> events) {
    try {
      return stateMachine.fold(events.stream().map(CaptureService::eventType).toList());
    } catch (IllegalArgumentException exception) {
      throw internal();
    }
  }

  private static TransactionEventType eventType(PaymentEvent event) {
    return Arrays.stream(TransactionEventTypes.values())
        .map(TransactionEventTypes::getValue)
        .filter(type -> type.getTransactionEventTypeId() == event.transactionEventTypeId())
        .findFirst()
        .orElseThrow(CaptureService::internal);
  }

  private static Currency currency(long currencyId) {
    return Arrays.stream(Currencies.values())
        .map(Currencies::getValue)
        .filter(value -> value.getCurrencyId() == currencyId)
        .findFirst()
        .orElseThrow(CaptureService::internal);
  }

  private static CaptureException internal() {
    return new CaptureException(500, "INTERNAL_ERROR");
  }
}
