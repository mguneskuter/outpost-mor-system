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
import com.outpost.accounting.api.CaptureRequest;
import com.outpost.accounting.api.CaptureResponse;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.templates.CaptureJournalTemplates;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.repository.CaptureChild;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentFamily;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PendingFee;
import com.outpost.payment.common.Amount;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates one immutable PSP capture attempt and its accounting evidence. */
public class CaptureService {
  private final PaymentRepository repository;
  private final JournalEntryRepository journalEntryRepository;
  private final Clock clock;
  private final com.outpost.accounting.payment.PaymentLifecycle lifecycle =
      new com.outpost.accounting.payment.PaymentLifecycle();

  /** Creates a service using the ledger clock and persistence seams. */
  public CaptureService(
      PaymentRepository repository, JournalEntryRepository journalEntryRepository, Clock clock) {
    this.repository = repository;
    this.journalEntryRepository = journalEntryRepository;
    this.clock = clock;
  }

  /** Stores a successful or failed capture, or returns the exact prior result. */
  @Transactional
  public CaptureResponse capture(CaptureRequest request) {
    validate(request);
    Currency currency =
        Currencies.fromCurrencyCode(request.currency()).orElseThrow(CaptureService::bad);
    PaymentFamily payment = repository.findPaymentFamilyForUpdate(request.paymentReference());
    if (payment == null) {
      throw notFound();
    }

    CaptureChild existing = repository.findCaptureChild(payment.transactionId());
    if (existing != null) {
      if (!existing.reference().equals(request.captureReference())) {
        throw invalidCapture();
      }
      return replayOrReject(request, currency, existing);
    }
    if (repository.findCaptureByReference(request.captureReference()) != null
        || repository.findByReference(request.captureReference()) != null) {
      throw conflict();
    }

    TransactionEventType transactionEventType = transactionEventType(request.success());
    List<PaymentEvent> events = repository.findPaymentEvents(payment.transactionId());
    TransactionEventType paymentState = fold(events);
    if (!lifecycle.canFollowCapture(paymentState, false, transactionEventType)
        || payment.currencyId() != currency.getCurrencyId()
        || payment.grossQuantity() != request.amount()) {
      throw invalidCapture();
    }
    PendingFee pendingFee = repository.findPendingFee(payment.transactionId());
    if (!validPendingFee(payment, pendingFee)) {
      throw internal();
    }

    Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    Long captureTransactionId =
        repository.insertCaptureTransaction(
            payment.transactionId(),
            payment.merchantAccountId(),
            request.captureReference(),
            request.amount(),
            currency.getCurrencyId(),
            occurredAt);
    if (captureTransactionId == null) {
      throw conflict();
    }
    Long eventId =
        repository.insertPaymentEvent(
            captureTransactionId, transactionEventType.getTransactionEventTypeId(), occurredAt);
    if (eventId == null) {
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
              eventId,
              Transaction.of(
                  captureTransactionId,
                  TransactionTypes.CAPTURE.getValue(),
                  merchantAccount,
                  request.captureReference(),
                  new Amount(currency, payment.grossQuantity()),
                  occurredAt),
              transactionEventType,
              occurredAt);
    } catch (IllegalArgumentException exception) {
      throw internal();
    }
    journalEntryRepository.insertJournalEntry(journalEntry(payment, pendingFee, transactionEvent));
    return new CaptureResponse(request.captureReference(), occurredAt);
  }

  private CaptureResponse replayOrReject(
      CaptureRequest request, Currency currency, CaptureChild existing) {
    if (!existing.reference().equals(request.captureReference())
        || existing.quantity() != request.amount()
        || existing.currencyId() != currency.getCurrencyId()
        || existing.eventTypeId() == null
        || existing.eventTypeId()
            != transactionEventType(request.success()).getTransactionEventTypeId()) {
      throw conflict();
    }
    return new CaptureResponse(existing.reference(), existing.createdTs());
  }

  private JournalEntry journalEntry(
      PaymentFamily payment, PendingFee pendingFee, TransactionEvent transactionEvent) {
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

  private static boolean validPendingFee(PaymentFamily payment, PendingFee fee) {
    return fee != null
        && fee.fee() >= 0
        && fee.fee() <= payment.netQuantity()
        && fee.currencyId() == payment.currencyId();
  }

  private static TransactionEventType fold(List<PaymentEvent> events) {
    try {
      return new com.outpost.accounting.payment.PaymentLifecycle()
          .fold(events.stream().map(CaptureService::eventType).toList());
    } catch (IllegalArgumentException exception) {
      throw internal();
    }
  }

  private static TransactionEventType eventType(PaymentEvent event) {
    return Arrays.stream(TransactionEventTypes.values())
        .map(com.outpost.accounting.TransactionEventTypes::getValue)
        .filter(type -> type.getTransactionEventTypeId() == event.transactionEventTypeId())
        .findFirst()
        .orElseThrow(CaptureService::internal);
  }

  private static TransactionEventType transactionEventType(boolean success) {
    return success
        ? TransactionEventTypes.CAPTURED.getValue()
        : TransactionEventTypes.CAPTURE_FAILED.getValue();
  }

  private static void validate(CaptureRequest request) {
    if (request == null
        || request.paymentReference() == null
        || request.paymentReference().isBlank()
        || request.captureReference() == null
        || request.captureReference().isBlank()
        || request.success() == null
        || request.amount() == null
        || request.amount() <= 0
        || request.currency() == null
        || request.currency().isBlank()) {
      throw bad();
    }
  }

  private static CaptureException bad() {
    return new CaptureException(400, "INVALID_REQUEST");
  }

  private static CaptureException notFound() {
    return new CaptureException(404, "PAYMENT_NOT_FOUND");
  }

  private static CaptureException conflict() {
    return new CaptureException(409, "REFERENCE_CONFLICT");
  }

  private static CaptureException invalidCapture() {
    return new CaptureException(422, "INVALID_CAPTURE");
  }

  private static CaptureException internal() {
    return new CaptureException(500, "INTERNAL_ERROR");
  }
}
