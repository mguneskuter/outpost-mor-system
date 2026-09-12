package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.accounting.payment.CreatePaymentCommand;
import com.outpost.accounting.payment.PaymentFeeCalculator;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.api.CreatePaymentRequest;
import com.outpost.ledger.payment.api.PaymentResponse;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.mybatis.AccountRow;
import com.outpost.ledger.payment.repository.mybatis.ExistingPaymentRow;
import com.outpost.ledger.payment.repository.mybatis.FeeRow;
import com.outpost.payment.common.Amount;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates the atomic payment creation transaction. */
public class PaymentCreationService {
  private final PaymentRepository repository;
  private final PaymentFeeCalculator feeCalculator;
  private final Clock clock;

  /** Creates a service using the ledger clock and persistence seam. */
  public PaymentCreationService(
      PaymentRepository repository, PaymentFeeCalculator feeCalculator, Clock clock) {
    this.repository = repository;
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
      FeeRow feeRow = repository.findFee(merchant.getAccountId(), currency.getCurrencyId());
      if (feeRow == null || feeRow.currencyId() != currency.getCurrencyId()) {
        throw unprocessable("MISSING_FEE_CONFIGURATION");
      }
      String feeModeCode =
          switch ((int) feeRow.feeModeId()) {
            case 1 -> "PERCENTAGE";
            case 2 -> "PERCENTAGE_PLUS_FIXED";
            default -> throw unprocessable("MISSING_FEE_CONFIGURATION");
          };
      MerchantFeeConfiguration feeConfiguration =
          new MerchantFeeConfiguration(
              feeRow.merchantFeeConfigurationId(),
              merchant,
              currency,
              FeeModes.fromCode(feeModeCode).orElseThrow(),
              feeRow.feeRateBps(),
              feeRow.feeFixed() == null ? null : new Amount(currency, feeRow.feeFixed()));
      long fee;
      try {
        fee =
            feeCalculator
                .calculate(new Amount(currency, request.netAmount()), feeConfiguration)
                .quantity();
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
      Long taxAuthority = repository.findTaxAuthority(country.getCountryId());
      Long platform = repository.findPlatform();
      Long merchantRegister = repository.findPendingRegister(merchant.getAccountId());
      Long platformRegister = platform == null ? null : repository.findPendingRegister(platform);
      if (taxAuthority == null
          || platform == null
          || merchantRegister == null
          || platformRegister == null) {
        throw unprocessable("MISSING_ACCOUNT");
      }
      repository.insertPaymentDetail(
          transactionId,
          country.getCountryId(),
          subdivision == null ? null : subdivision.getCountrySubdivisionId(),
          psp.getAccountId(),
          request.netAmount(),
          request.taxAmount());
      long eventId = repository.insertEvent(transactionId, createdAt);
      long entryId = repository.insertEntry(eventId, createdAt);
      repository.insertLine(entryId, merchantRegister, currency.getCurrencyId(), fee);
      repository.insertLine(entryId, platformRegister, currency.getCurrencyId(), -fee);
      return new PaymentResponse(request.paymentReference(), createdAt);
    } catch (PaymentCreationException e) {
      throw e;
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw bad();
    } catch (RuntimeException e) {
      throw e;
    }
  }

  private PaymentResponse existingOrConflict(
      CreatePaymentRequest r,
      Currency c,
      Account m,
      Account p,
      Country country,
      CountrySubdivision subdivision) {
    ExistingPaymentRow e = repository.findByReference(r.paymentReference());
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
    AccountRow row = repository.findAccount(code);
    if (row == null
        || row.accountTypeId() != expected.getValue().getAccountTypeId()
        || !row.isActive()) {
      throw unknown();
    }
    Account parent = row.parentAccountId() == null ? null : accountById(row.parentAccountId());
    return Account.of(
        row.accountId(),
        expected.getValue(),
        row.code(),
        row.name(),
        row.isActive(),
        row.createdTs(),
        parent);
  }

  private Account accountById(long id) {
    AccountRow row = repository.findAccountById(id);
    if (row == null) {
      throw unknown();
    }
    Account parent = row.parentAccountId() == null ? null : accountById(row.parentAccountId());
    AccountTypes type =
        java.util.Arrays.stream(AccountTypes.values())
            .filter(v -> v.getValue().getAccountTypeId() == row.accountTypeId())
            .findFirst()
            .orElseThrow(PaymentCreationService::unknown);
    return Account.of(
        row.accountId(),
        type.getValue(),
        row.code(),
        row.name(),
        row.isActive(),
        row.createdTs(),
        parent);
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
}
