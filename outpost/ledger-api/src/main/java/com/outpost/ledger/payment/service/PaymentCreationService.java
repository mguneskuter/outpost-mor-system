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
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentFeeCalculator;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.repository.ExistingPayment;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.StoredTransaction;
import com.outpost.payment.common.Amount;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Books a payment's creation: its PAYMENT transaction, detail, ORDER_CREATED event, and pending-fee
 * entry.
 */
public class PaymentCreationService {
  private final PaymentRepository repository;
  private final JournalEntryRepository journalEntryRepository;
  private final PaymentFeeCalculator feeCalculator;

  /** Creates a service using the persistence seams. */
  public PaymentCreationService(
      PaymentRepository repository,
      JournalEntryRepository journalEntryRepository,
      PaymentFeeCalculator feeCalculator) {
    this.repository = repository;
    this.journalEntryRepository = journalEntryRepository;
    this.feeCalculator = feeCalculator;
  }

  /**
   * Books the payment for an order in one database transaction. A repeat whose stored payment
   * matches every field writes nothing.
   *
   * @throws PaymentCreationException 400 INVALID_REQUEST, 409 REFERENCE_CONFLICT, 422
   *     UNKNOWN_ACCOUNT, INCONSISTENT_AMOUNTS, MISSING_FEE_CONFIGURATION, FEE_ABOVE_NET, or
   *     MISSING_ACCOUNT
   */
  @Transactional
  public void create(AccountingQueueRequest request) {
    if (request == null) {
      throw bad();
    }
    try {
      validate(request);
      Amount netAmount = request.netAmount();
      Amount taxAmount = request.taxAmount();
      Amount grossAmount = request.grossAmount();
      Currency currency = grossAmount.currency();
      if (!netAmount.currency().equals(currency)
          || !taxAmount.currency().equals(currency)
          || Math.addExact(netAmount.quantity(), taxAmount.quantity()) != grossAmount.quantity()) {
        throw unprocessable("INCONSISTENT_AMOUNTS");
      }
      Country country = request.shopperCountry();
      CountrySubdivision subdivision = request.shopperCountrySubdivision();
      Account merchant = account(request.merchantCode(), AccountTypes.MERCHANT);
      Account psp = account(request.pspCode(), AccountTypes.PSP);
      MerchantFeeConfiguration feeConfiguration =
          repository.findFee(merchant.getAccountId(), currency.getCurrencyId());
      if (feeConfiguration == null
          || feeConfiguration.currency().getCurrencyId() != currency.getCurrencyId()) {
        throw unprocessable("MISSING_FEE_CONFIGURATION");
      }
      Amount fee;
      try {
        fee = feeCalculator.calculate(netAmount, feeConfiguration);
      } catch (IllegalArgumentException | ArithmeticException e) {
        throw unprocessable("FEE_ABOVE_NET");
      }
      StoredTransaction payment =
          repository.insertTransaction(
              merchant.getAccountId(),
              request.originalReference(),
              grossAmount.quantity(),
              currency.getCurrencyId());
      if (payment == null) {
        existingOrConflict(request, currency, merchant, psp, country, subdivision);
        return;
      }
      long transactionId = payment.transactionId();
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
          netAmount.quantity(),
          taxAmount.quantity());
      PaymentEvent orderCreatedEvent = repository.insertEvent(transactionId);
      TransactionEvent orderCreated =
          new TransactionEvent(
              orderCreatedEvent.transactionEventId(),
              Transaction.of(
                  transactionId,
                  TransactionTypes.PAYMENT.getValue(),
                  merchant,
                  request.originalReference(),
                  grossAmount,
                  payment.createdAt()),
              TransactionEventTypes.ORDER_CREATED.getValue(),
              orderCreatedEvent.occurredAt());
      journalEntryRepository.insertJournalEntry(
          pendingFeeEntry(orderCreated, merchantPendingFee, platformPendingFee, fee));
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

  private void existingOrConflict(
      AccountingQueueRequest r,
      Currency c,
      Account m,
      Account p,
      Country country,
      CountrySubdivision subdivision) {
    ExistingPayment e = repository.findByReference(r.originalReference());
    if (e != null
        && e.accountId() == m.getAccountId()
        && e.pspAccountId() == p.getAccountId()
        && e.quantity() == r.grossAmount().quantity()
        && e.currencyId() == c.getCurrencyId()
        && e.netQuantity() == r.netAmount().quantity()
        && e.taxQuantity() == r.taxAmount().quantity()
        && e.shopperCountryId() == country.getCountryId()
        && Objects.equals(
            e.shopperCountrySubdivisionId(),
            subdivision == null ? null : subdivision.getCountrySubdivisionId())) {
      return;
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

  private static void validate(AccountingQueueRequest r) {
    if (r.originalReference() == null
        || r.originalReference().isBlank()
        || r.merchantCode() == null
        || r.pspCode() == null
        || r.shopperCountry() == null
        || r.netAmount() == null
        || r.taxAmount() == null
        || r.grossAmount() == null
        || r.netAmount().quantity() < 0
        || r.taxAmount().quantity() < 0
        || r.grossAmount().quantity() <= 0) {
      throw bad();
    }
  }

  private static PaymentCreationException bad() {
    return new PaymentCreationException(400, "INVALID_REQUEST");
  }

  private static PaymentCreationException unknown() {
    return new PaymentCreationException(422, "UNKNOWN_ACCOUNT");
  }

  private static PaymentCreationException unprocessable(String code) {
    return new PaymentCreationException(422, code);
  }

  private static PaymentCreationException internal() {
    return new PaymentCreationException(500, "INTERNAL_ERROR");
  }
}
