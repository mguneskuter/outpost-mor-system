package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.CreatePaymentCommand;
import com.outpost.accounting.payment.PaymentFeeCalculator;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.api.CreatePaymentRequest;
import com.outpost.ledger.payment.api.PaymentResponse;
import com.outpost.ledger.payment.repository.ExistingPayment;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.payment.common.Amount;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates the atomic payment creation transaction. */
public class PaymentCreationService {
  private final PaymentRepository repository;
  private final JournalEntryRepository journalEntryRepository;
  private final PaymentFeeCalculator feeCalculator;
  private final Clock clock;

  /** Creates a service using the ledger clock and persistence seams. */
  public PaymentCreationService(
      PaymentRepository repository,
      JournalEntryRepository journalEntryRepository,
      PaymentFeeCalculator feeCalculator,
      Clock clock) {
    this.repository = repository;
    this.journalEntryRepository = journalEntryRepository;
    this.feeCalculator = feeCalculator;
    this.clock = clock;
  }

  /** Creates a payment and its pending-fee journal atomically. */
  @Transactional
  public PaymentResponse create(CreatePaymentRequest request) {
    if (request == null) {
      throw bad();
    }
    try {
      validate(request);
      Currency currency =
          Currencies.fromCurrencyCode(request.currency()).orElseThrow(PaymentCreationService::bad);
      Country country =
          Countries.fromIsoCode(request.shopperCountry()).orElseThrow(PaymentCreationService::bad);
      CountrySubdivision subdivision =
          request.shopperCountrySubdivision() == null
              ? null
              : CountrySubdivisions.fromCode(country, request.shopperCountrySubdivision())
                  .orElseThrow(PaymentCreationService::subdivision);
      Account merchant = account(request.merchantCode(), AccountTypes.MERCHANT);
      Account psp = account(request.pspCode(), AccountTypes.PSP);
      new CreatePaymentCommand(
          merchant,
          psp,
          request.paymentReference(),
          new Amount(currency, request.netAmount()),
          new Amount(currency, request.taxAmount()),
          country,
          subdivision);
      long gross = Math.addExact(request.netAmount(), request.taxAmount());
      if (gross != request.grossAmount()) {
        throw unprocessable("INCONSISTENT_AMOUNTS");
      }
      MerchantFeeConfiguration feeConfiguration =
          repository.findFee(merchant.getAccountId(), currency.getCurrencyId());
      if (feeConfiguration == null
          || feeConfiguration.currency().getCurrencyId() != currency.getCurrencyId()) {
        throw unprocessable("MISSING_FEE_CONFIGURATION");
      }
      Amount fee;
      try {
        fee = feeCalculator.calculate(new Amount(currency, request.netAmount()), feeConfiguration);
      } catch (IllegalArgumentException | ArithmeticException e) {
        throw unprocessable("FEE_ABOVE_NET");
      }
      Instant createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
      Long transactionId =
          repository.insertTransaction(
              merchant.getAccountId(),
              request.paymentReference(),
              gross,
              currency.getCurrencyId(),
              createdAt);
      if (transactionId == null) {
        return existingOrConflict(request, currency, merchant, psp, country, subdivision);
      }
      Account taxAuthorityAccount =
          repository.findTaxAuthorityAccountByCountryId(country.getCountryId());
      Account platformAccount = repository.findPlatformAccount();
      if (taxAuthorityAccount == null || platformAccount == null) {
        throw unprocessable("MISSING_ACCOUNT");
      }
      Register merchantPendingFee = pendingFeeRegister(merchant);
      Register platformPendingFee = pendingFeeRegister(platformAccount);
      repository.insertPaymentDetail(
          transactionId,
          country.getCountryId(),
          subdivision == null ? null : subdivision.getCountrySubdivisionId(),
          psp.getAccountId(),
          request.netAmount(),
          request.taxAmount());
      long eventId = repository.insertEvent(transactionId, createdAt);
      TransactionEvent orderCreated =
          new TransactionEvent(
              eventId,
              Transaction.of(
                  transactionId,
                  TransactionTypes.PAYMENT.getValue(),
                  merchant,
                  request.paymentReference(),
                  new Amount(currency, gross),
                  createdAt),
              TransactionEventTypes.ORDER_CREATED.getValue(),
              createdAt);
      journalEntryRepository.insertJournalEntry(
          pendingFeeEntry(orderCreated, merchantPendingFee, platformPendingFee, fee));
      return new PaymentResponse(request.paymentReference(), createdAt);
    } catch (PaymentCreationException e) {
      throw e;
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw bad();
    } catch (RuntimeException e) {
      throw e;
    }
  }

  private Register pendingFeeRegister(Account account) {
    Register register;
    try {
      register =
          repository.findRegister(
              account.getAccountId(), RegisterTypes.PENDING_FEE.getValue().getRegisterTypeId());
    } catch (IllegalArgumentException e) {
      throw internal();
    }
    if (register == null) {
      throw unprocessable("MISSING_ACCOUNT");
    }
    if (register.getAccount().getAccountId() != account.getAccountId()) {
      throw internal();
    }
    return register;
  }

  private static JournalEntry pendingFeeEntry(
      TransactionEvent orderCreated,
      Register merchantPendingFee,
      Register platformPendingFee,
      Amount fee) {
    try {
      return PendingFeeJournalTemplates.FEE_PENDING.build(
          orderCreated, merchantPendingFee, platformPendingFee, fee, orderCreated.getOccurredAt());
    } catch (IllegalArgumentException e) {
      throw internal();
    }
  }

  private PaymentResponse existingOrConflict(
      CreatePaymentRequest r,
      Currency c,
      Account m,
      Account p,
      Country country,
      CountrySubdivision subdivision) {
    ExistingPayment e = repository.findByReference(r.paymentReference());
    if (e != null
        && e.accountId() == m.getAccountId()
        && e.pspAccountId() == p.getAccountId()
        && e.quantity() == r.grossAmount()
        && e.currencyId() == c.getCurrencyId()
        && e.netQuantity() == r.netAmount()
        && e.taxQuantity() == r.taxAmount()
        && e.shopperCountryId() == country.getCountryId()
        && Objects.equals(
            e.shopperCountrySubdivisionId(),
            subdivision == null ? null : subdivision.getCountrySubdivisionId())) {
      return new PaymentResponse(e.reference(), e.createdTs());
    }
    throw new PaymentCreationException(409, "REFERENCE_CONFLICT");
  }

  private Account account(String code, AccountTypes expected) {
    Account account = repository.findAccount(code);
    if (account == null
        || !account.getAccountType().equals(expected.getValue())
        || !account.isActive()) {
      throw unknown();
    }
    return account;
  }

  private static void validate(CreatePaymentRequest r) {
    if (r.paymentReference() == null
        || r.paymentReference().isBlank()
        || r.merchantCode() == null
        || r.pspCode() == null
        || r.shopperCountry() == null
        || r.currency() == null
        || r.netAmount() == null
        || r.taxAmount() == null
        || r.grossAmount() == null
        || r.netAmount() < 0
        || r.taxAmount() < 0
        || r.grossAmount() <= 0) {
      throw bad();
    }
  }

  private static PaymentCreationException bad() {
    return new PaymentCreationException(400, "INVALID_REQUEST");
  }

  private static PaymentCreationException unknown() {
    return new PaymentCreationException(422, "UNKNOWN_ACCOUNT");
  }

  private static PaymentCreationException subdivision() {
    return new PaymentCreationException(422, "INVALID_SUBDIVISION");
  }

  private static PaymentCreationException unprocessable(String code) {
    return new PaymentCreationException(422, code);
  }

  private static PaymentCreationException internal() {
    return new PaymentCreationException(500, "INTERNAL_ERROR");
  }
}
