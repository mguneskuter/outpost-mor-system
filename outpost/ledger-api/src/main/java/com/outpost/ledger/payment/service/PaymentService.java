package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentFeeCalculator;
import com.outpost.accounting.repository.RegisterRepository;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.payment.common.Amount;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Books a payment's creation: its PAYMENT transaction, detail, ORDER_CREATED event, and pending-fee
 * entry.
 */
public class PaymentService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(PaymentService.class));
  private final TransactionRepository transactions;
  private final JournalEntryRepository journalEntries;
  private final AccountRepository accounts;
  private final RegisterRepository registers;
  private final MerchantFeeConfigurationRepository feeConfigurations;
  private final PaymentFeeCalculator feeCalculator;

  /** Creates a service over the repositories it books through and the fee policy. */
  public PaymentService(
      TransactionRepository transactions,
      JournalEntryRepository journalEntries,
      AccountRepository accounts,
      RegisterRepository registers,
      MerchantFeeConfigurationRepository feeConfigurations,
      PaymentFeeCalculator feeCalculator) {
    this.transactions = transactions;
    this.journalEntries = journalEntries;
    this.accounts = accounts;
    this.registers = registers;
    this.feeConfigurations = feeConfigurations;
    this.feeCalculator = feeCalculator;
  }

  /**
   * Books the payment for an order in one database transaction. A repeat whose stored payment
   * matches every field writes nothing.
   *
   * @throws BookingException when the request is not booked; its code says why
   */
  @Transactional
  public void bookPayment(AccountingQueueRequest request) {
    if (request == null) {
      throw invalidRequest(null);
    }
    String originalReference = request.originalReference();
    String merchantCode = request.merchantCode();
    String pspCode = request.pspCode();
    Country shopperCountry = request.shopperCountry();
    Amount netAmount = request.netAmount();
    Amount taxAmount = request.taxAmount();
    Amount grossAmount = request.grossAmount();
    if (originalReference == null
        || originalReference.isBlank()
        || merchantCode == null
        || pspCode == null
        || shopperCountry == null
        || netAmount == null
        || taxAmount == null
        || grossAmount == null
        || netAmount.quantity() < 0
        || taxAmount.quantity() < 0
        || grossAmount.quantity() <= 0) {
      throw invalidRequest(null);
    }
    try {
      Currency currency = grossAmount.currency();
      if (!netAmount.currency().equals(currency)
          || !taxAmount.currency().equals(currency)
          || Math.addExact(netAmount.quantity(), taxAmount.quantity()) != grossAmount.quantity()) {
        throw refused(BookingErrorCodes.INCONSISTENT_AMOUNTS);
      }
      Account merchant = activeAccount(merchantCode, AccountTypes.MERCHANT);
      Account psp = activeAccount(pspCode, AccountTypes.PSP);
      MerchantFeeConfiguration feeConfiguration =
          feeConfigurations
              .findMerchantFeeConfigurationByAccountAndCurrency(merchant, currency)
              .orElseThrow(() -> refused(BookingErrorCodes.MISSING_FEE_CONFIGURATION));
      Amount fee;
      try {
        fee = feeCalculator.calculate(netAmount, feeConfiguration);
      } catch (IllegalArgumentException | ArithmeticException e) {
        throw new BookingException(BookingErrorCodes.FEE_ABOVE_NET, e);
      }
      PaymentDetail requested =
          new PaymentDetail(
              Transaction.of(
                  null,
                  TransactionTypes.PAYMENT.getValue(),
                  merchant,
                  originalReference,
                  grossAmount,
                  null),
              shopperCountry,
              request.shopperCountrySubdivision(),
              psp,
              netAmount,
              taxAmount);
      Optional<PaymentDetail> stored = transactions.insertPaymentDetail(requested);
      if (stored.isEmpty()) {
        answerRepeatedPayment(requested);
        return;
      }
      Transaction payment = stored.get().getPaymentTransaction();
      if (accounts.findTaxAuthorityAccountByCountryId(shopperCountry.getCountryId()).isEmpty()) {
        throw refused(BookingErrorCodes.MISSING_ACCOUNT);
      }
      Account platform =
          accounts
              .findAccountByAccountType(AccountTypes.PLATFORM.getValue())
              .orElseThrow(() -> refused(BookingErrorCodes.MISSING_ACCOUNT));
      Register merchantPendingFee = pendingFeeRegister(merchant);
      Register platformPendingFee = pendingFeeRegister(platform);
      TransactionEvent orderCreated =
          transactions
              .insertTransactionEvent(payment, TransactionEventTypes.ORDER_CREATED.getValue())
              .orElseThrow(() -> inconsistentBooking(null));
      journalEntries.insertJournalEntry(
          pendingFeeEntry(orderCreated, merchantPendingFee, platformPendingFee, fee));
      LOGGER.info(
          "Payment booked with its pending fee",
          new StructuredLogField(LogFields.ORIGINAL_REFERENCE, originalReference),
          new StructuredLogField(LogFields.MERCHANT_CODE, merchant.getCode()),
          new StructuredLogField(LogFields.PSP_CODE, psp.getCode()),
          new StructuredLogField(LogFields.CURRENCY, currency.getCurrencyCode()),
          new StructuredLogField(LogFields.GROSS_AMOUNT, Long.toString(grossAmount.quantity())),
          new StructuredLogField(LogFields.FEE_AMOUNT, Long.toString(fee.quantity())));
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw invalidRequest(e);
    }
  }

  private Register pendingFeeRegister(Account account) {
    return registers
        .findRegisterByAccountAndRegisterType(account, RegisterTypes.PENDING_FEE.getValue())
        .orElseThrow(() -> refused(BookingErrorCodes.MISSING_ACCOUNT));
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
      throw inconsistentBooking(e);
    }
  }

  private void answerRepeatedPayment(PaymentDetail requested) {
    String reference = requested.getPaymentTransaction().getReference();
    PaymentDetail stored =
        transactions
            .findPaymentDetailByReference(reference)
            .filter(payment -> isSamePayment(payment, requested))
            .orElseThrow(() -> refused(BookingErrorCodes.REFERENCE_CONFLICT));
    LOGGER.info(
        "Payment already booked",
        new StructuredLogField(
            LogFields.ORIGINAL_REFERENCE, stored.getPaymentTransaction().getReference()));
  }

  private static boolean isSamePayment(PaymentDetail stored, PaymentDetail requested) {
    Transaction storedPayment = stored.getPaymentTransaction();
    Transaction requestedPayment = requested.getPaymentTransaction();
    return storedPayment.getMerchantAccount().getAccountId()
            == requestedPayment.getMerchantAccount().getAccountId()
        && stored.getPspAccount().getAccountId() == requested.getPspAccount().getAccountId()
        && storedPayment.getAmount().equals(requestedPayment.getAmount())
        && stored.getNetAmount().equals(requested.getNetAmount())
        && stored.getTaxAmount().equals(requested.getTaxAmount())
        && stored.getShopperCountry().equals(requested.getShopperCountry())
        && stored.getShopperCountrySubdivision().equals(requested.getShopperCountrySubdivision());
  }

  private Account activeAccount(String code, AccountTypes accountType) {
    return accounts
        .findAccountByCode(code)
        .filter(
            account ->
                account.getAccountType().equals(accountType.getValue()) && account.isActive())
        .orElseThrow(() -> refused(BookingErrorCodes.UNKNOWN_ACCOUNT));
  }

  private static BookingException invalidRequest(@Nullable Throwable cause) {
    return new BookingException(BookingErrorCodes.INVALID_REQUEST, cause);
  }

  private static BookingException refused(BookingErrorCodes code) {
    return new BookingException(code, null);
  }

  private static BookingException inconsistentBooking(@Nullable Throwable cause) {
    return new BookingException(BookingErrorCodes.INCONSISTENT_BOOKING, cause);
  }
}
