package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.CaptureJournalTemplates;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.api.CaptureRequest;
import com.outpost.ledger.payment.api.CaptureResponse;
import com.outpost.ledger.payment.repository.CaptureChild;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentFamily;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PendingFee;
import com.outpost.ledger.payment.repository.mybatis.AccountRow;
import com.outpost.ledger.payment.repository.mybatis.RegisterRow;
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
  private final Clock clock;
  private final com.outpost.accounting.payment.PaymentLifecycle lifecycle =
      new com.outpost.accounting.payment.PaymentLifecycle();

  /** Creates a service using the ledger clock and persistence seam. */
  public CaptureService(PaymentRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /** Records a successful or failed capture, or returns the exact prior result. */
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

    TransactionEventType outcome = outcome(request.success());
    List<PaymentEvent> events = repository.findPaymentEvents(payment.transactionId());
    TransactionEventType paymentState = fold(events);
    if (!lifecycle.canFollowCapture(paymentState, false, outcome)
        || payment.currencyId() != currency.getCurrencyId()
        || payment.grossQuantity() != request.amount()) {
      throw invalidCapture();
    }
    PendingFee pendingFee = repository.findPendingFee(payment.transactionId());
    if (!validPendingFee(payment, pendingFee)) {
      throw internal();
    }

    Instant occurredAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    Long captureId =
        repository.insertCaptureTransaction(
            payment.transactionId(),
            payment.merchantAccountId(),
            request.captureReference(),
            request.amount(),
            currency.getCurrencyId(),
            occurredAt);
    if (captureId == null) {
      throw conflict();
    }
    Long eventId =
        repository.insertPaymentEvent(captureId, outcome.getTransactionEventTypeId(), occurredAt);
    if (eventId == null) {
      throw internal();
    }
    if (request.success()) {
      JournalEntry captureEntry =
          buildCaptureEntry(
              payment,
              pendingFee,
              currency,
              request.captureReference(),
              captureId,
              eventId,
              occurredAt);
      long entryId =
          repository.insertFeeReleaseEntry(
              eventId, JournalEntryTypes.CAPTURE.getValue().getJournalEntryTypeId(), occurredAt);
      persistLines(entryId, captureEntry);
    } else {
      long entryId =
          repository.insertFeeReleaseEntry(
              eventId,
              JournalEntryTypes.FEE_RELEASE.getValue().getJournalEntryTypeId(),
              occurredAt);
      repository.insertLine(
          entryId, pendingFee.merchantRegisterId(), pendingFee.currencyId(), -pendingFee.fee());
      repository.insertLine(
          entryId, pendingFee.platformRegisterId(), pendingFee.currencyId(), pendingFee.fee());
    }
    return new CaptureResponse(request.captureReference(), occurredAt);
  }

  private CaptureResponse replayOrReject(
      CaptureRequest request, Currency currency, CaptureChild existing) {
    if (!existing.reference().equals(request.captureReference())
        || existing.quantity() != request.amount()
        || existing.currencyId() != currency.getCurrencyId()
        || existing.eventTypeId() == null
        || existing.eventTypeId() != outcome(request.success()).getTransactionEventTypeId()) {
      throw conflict();
    }
    return new CaptureResponse(existing.reference(), existing.createdTs());
  }

  private JournalEntry buildCaptureEntry(
      PaymentFamily payment,
      PendingFee pendingFee,
      Currency currency,
      String captureReference,
      long captureId,
      long eventId,
      Instant occurredAt) {
    RegisterRow psp =
        register(
            payment.pspAccountId(), RegisterTypes.PSP_RECEIVABLE.getValue().getRegisterTypeId());
    Long taxAuthority = repository.findTaxAuthority(payment.shopperCountryId());
    Long platform = repository.findPlatform();
    RegisterRow tax =
        taxAuthority == null
            ? null
            : register(taxAuthority, RegisterTypes.TAX_PAYABLE.getValue().getRegisterTypeId());
    RegisterRow merchantPayable =
        register(
            payment.merchantAccountId(),
            RegisterTypes.MERCHANT_PAYABLE.getValue().getRegisterTypeId());
    RegisterRow feeRevenue =
        platform == null
            ? null
            : register(platform, RegisterTypes.FEE_REVENUE.getValue().getRegisterTypeId());
    RegisterRow merchantPending =
        register(
            payment.merchantAccountId(), RegisterTypes.PENDING_FEE.getValue().getRegisterTypeId());
    RegisterRow platformPending =
        platform == null
            ? null
            : register(platform, RegisterTypes.PENDING_FEE.getValue().getRegisterTypeId());
    if (tax == null
        || feeRevenue == null
        || platformPending == null
        || merchantPending.registerId() != pendingFee.merchantRegisterId()
        || platformPending.registerId() != pendingFee.platformRegisterId()
        || psp.accountId() != payment.pspAccountId()
        || tax.accountId() != taxAuthority
        || merchantPayable.accountId() != payment.merchantAccountId()
        || feeRevenue.accountId() != platform) {
      throw internal();
    }
    try {
      Account merchantAccount = domainRegister(merchantPayable).getAccount();
      Transaction captureTransaction =
          Transaction.of(
              captureId,
              TransactionTypes.CAPTURE.getValue(),
              merchantAccount,
              captureReference,
              new Amount(currency, payment.grossQuantity()),
              occurredAt);
      TransactionEvent captureEvent =
          new TransactionEvent(
              eventId, captureTransaction, TransactionEventTypes.CAPTURED.getValue(), occurredAt);
      return CaptureJournalTemplates.CAPTURE.build(
          1L,
          1L,
          2L,
          3L,
          4L,
          5L,
          6L,
          captureEvent,
          domainRegister(psp),
          domainRegister(tax),
          domainRegister(merchantPayable),
          domainRegister(feeRevenue),
          domainRegister(merchantPending),
          domainRegister(platformPending),
          new Amount(currency, payment.grossQuantity()),
          new Amount(currency, payment.netQuantity()),
          new Amount(currency, payment.taxQuantity()),
          new Amount(currency, pendingFee.fee()),
          occurredAt);
    } catch (CaptureException exception) {
      throw exception;
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw internal();
    }
  }

  private void persistLines(long entryId, JournalEntry journalEntry) {
    for (JournalEntryLine line : journalEntry.getJournalEntryLines()) {
      repository.insertLine(
          entryId,
          line.getRegister().getRegisterId(),
          line.getAmount().currency().getCurrencyId(),
          line.getAmount().quantity());
    }
  }

  private Register domainRegister(RegisterRow row) {
    Account account = account(row.accountId());
    if (account.getAccountType().getAccountTypeId() != row.accountTypeId()) {
      throw internal();
    }
    RegisterType registerType =
        Arrays.stream(RegisterTypes.values())
            .map(RegisterTypes::getValue)
            .filter(type -> type.getRegisterTypeId() == row.registerTypeId())
            .findFirst()
            .orElseThrow(CaptureService::internal);
    return new Register(row.registerId(), account, registerType);
  }

  private Account account(long accountId) {
    AccountRow row = repository.findAccountById(accountId);
    if (row == null) {
      throw internal();
    }
    Account parent = row.parentAccountId() == null ? null : account(row.parentAccountId());
    AccountTypes accountType =
        Arrays.stream(AccountTypes.values())
            .filter(type -> type.getValue().getAccountTypeId() == row.accountTypeId())
            .findFirst()
            .orElseThrow(CaptureService::internal);
    try {
      return Account.of(
          row.accountId(),
          accountType.getValue(),
          row.code(),
          row.name(),
          row.isActive(),
          row.createdTs(),
          parent);
    } catch (IllegalArgumentException exception) {
      throw internal();
    }
  }

  private RegisterRow register(long accountId, long registerTypeId) {
    RegisterRow row = repository.findRegister(accountId, registerTypeId);
    if (row == null) {
      throw missingRegister();
    }
    return row;
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

  private static TransactionEventType outcome(boolean success) {
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

  private static CaptureException missingRegister() {
    return internal();
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
