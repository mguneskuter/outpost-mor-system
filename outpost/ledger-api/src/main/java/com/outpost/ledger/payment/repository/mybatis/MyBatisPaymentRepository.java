package com.outpost.ledger.payment.repository.mybatis;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.repository.CaptureChild;
import com.outpost.ledger.payment.repository.CapturePosting;
import com.outpost.ledger.payment.repository.ExistingPayment;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentFamily;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PendingFee;
import com.outpost.ledger.payment.repository.RefundChild;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for payment persistence. */
@Repository
public class MyBatisPaymentRepository implements PaymentRepository {
  private final PaymentMapper mapper;

  /** Creates an adapter backed by the payment mapper. */
  public MyBatisPaymentRepository(PaymentMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ExistingPayment findByReference(String r) {
    ExistingPaymentRow row = mapper.findByReference(r);
    return row == null
        ? null
        : new ExistingPayment(
            row.transactionId(),
            row.accountId(),
            row.reference(),
            row.quantity(),
            row.currencyId(),
            row.createdTs(),
            row.pspAccountId(),
            row.shopperCountryId(),
            row.shopperCountrySubdivisionId(),
            row.netQuantity(),
            row.taxQuantity());
  }

  @Override
  public Account findAccount(String c) {
    return account(mapper.findAccount(c));
  }

  @Override
  public Account findAccountById(long i) {
    return account(mapper.findAccountById(i));
  }

  @Override
  public MerchantFeeConfiguration findFee(long a, long c) {
    FeeRow row = mapper.findFee(a, c);
    if (row == null) {
      return null;
    }
    Account account = findAccountById(row.accountId());
    Currency currency =
        Arrays.stream(Currencies.values())
            .map(Currencies::getValue)
            .filter(value -> value.getCurrencyId() == row.currencyId())
            .findFirst()
            .orElseThrow(IllegalArgumentException::new);
    var feeMode =
        Arrays.stream(FeeModes.values())
            .map(FeeModes::getValue)
            .filter(value -> value.getFeeModeId() == row.feeModeId())
            .findFirst()
            .orElseThrow(IllegalArgumentException::new);
    return new MerchantFeeConfiguration(
        row.merchantFeeConfigurationId(),
        account,
        currency,
        feeMode,
        row.feeRateBps(),
        row.feeFixed() == null ? null : new Amount(currency, row.feeFixed()));
  }

  @Override
  public Account findTaxAuthorityAccountByCountryId(long countryId) {
    return account(mapper.findTaxAuthorityAccountByCountryId(countryId));
  }

  @Override
  public Account findPlatformAccount() {
    return account(mapper.findPlatformAccount());
  }

  @Override
  public Long insertTransaction(long a, String r, long g, long c, Instant t) {
    return mapper.insertTransaction(a, r, g, c, t);
  }

  @Override
  public void insertPaymentDetail(long i, long c, Long s, long p, long n, long t) {
    mapper.insertPaymentDetail(i, c, s, p, n, t);
  }

  @Override
  public long insertEvent(long i, Instant t) {
    return mapper.insertEvent(i, t);
  }

  @Override
  public PaymentFamily findPaymentFamilyForUpdate(String r) {
    PaymentFamilyRow row = mapper.findPaymentFamilyForUpdate(r);
    return row == null
        ? null
        : new PaymentFamily(
            row.transactionId(),
            row.currencyId(),
            row.merchantAccountId(),
            row.pspAccountId(),
            row.shopperCountryId(),
            row.grossQuantity(),
            row.netQuantity(),
            row.taxQuantity());
  }

  @Override
  public List<PaymentEvent> findPaymentEvents(long i) {
    return mapper.findPaymentEvents(i).stream()
        .map(
            row ->
                new PaymentEvent(
                    row.transactionEventId(), row.transactionEventTypeId(), row.occurredAt()))
        .toList();
  }

  @Override
  public CaptureChild findCaptureChild(long i) {
    return capture(mapper.findCaptureChild(i));
  }

  @Override
  public CaptureChild findCaptureByReference(String r) {
    return capture(mapper.findCaptureByReference(r));
  }

  @Override
  public Register findRegister(long a, long t) {
    RegisterRow row = mapper.findRegister(a, t);
    if (row == null) {
      return null;
    }
    Account account = findAccountById(row.accountId());
    if (account.getAccountType().getAccountTypeId() != row.accountTypeId()) {
      throw new IllegalArgumentException("register account type does not match account");
    }
    var registerType =
        Arrays.stream(RegisterTypes.values())
            .map(RegisterTypes::getValue)
            .filter(value -> value.getRegisterTypeId() == row.registerTypeId())
            .findFirst()
            .orElseThrow(IllegalArgumentException::new);
    return new Register(row.registerId(), account, registerType);
  }

  @Override
  public Long insertCaptureTransaction(long p, long m, String r, long a, long c, Instant t) {
    return mapper.insertCaptureTransaction(p, m, r, a, c, t);
  }

  @Override
  public Long insertPaymentEvent(long i, long t, Instant at) {
    return mapper.insertPaymentEvent(i, t, at);
  }

  @Override
  public PendingFee findPendingFee(long i) {
    PendingFeeRow row = mapper.findPendingFee(i);
    return row == null
        ? null
        : new PendingFee(
            row.fee(), row.currencyId(), row.merchantRegisterId(), row.platformRegisterId());
  }

  @Override
  public boolean hasExactlyOneSuccessfulFullCapture(long i, long g, long c) {
    return mapper.hasExactlyOneSuccessfulFullCapture(i, g, c);
  }

  @Override
  public CapturePosting findCapturePosting(long i) {
    CapturePostingRow row = mapper.findCapturePosting(i);
    return row == null
        ? null
        : new CapturePosting(
            row.pspAccountId(),
            row.pspRegisterId(),
            row.taxAuthorityAccountId(),
            row.taxRegisterId(),
            row.merchantAccountId(),
            row.merchantRegisterId(),
            row.currencyId());
  }

  @Override
  public List<RefundChild> findRefundChildren(long i) {
    return mapper.findRefundChildren(i).stream()
        .map(
            row ->
                new RefundChild(
                    row.transactionId(),
                    row.paymentTransactionId(),
                    row.reference(),
                    row.quantity(),
                    row.currencyId(),
                    row.netQuantity(),
                    row.taxQuantity(),
                    row.createdTs(),
                    row.eventTypeId()))
        .toList();
  }

  @Override
  public RefundChild findRefundByReference(String r) {
    RefundChildRow row = mapper.findRefundByReference(r);
    return row == null
        ? null
        : new RefundChild(
            row.transactionId(),
            row.paymentTransactionId(),
            row.reference(),
            row.quantity(),
            row.currencyId(),
            row.netQuantity(),
            row.taxQuantity(),
            row.createdTs(),
            row.eventTypeId());
  }

  @Override
  public Long insertRefundTransaction(long p, long m, String r, long g, long c, Instant t) {
    return mapper.insertRefundTransaction(p, m, r, g, c, t);
  }

  @Override
  public void insertRefundDetail(long i, long n, long t) {
    mapper.insertRefundDetail(i, n, t);
  }

  private Account account(AccountRow row) {
    if (row == null) {
      return null;
    }
    Account parent =
        row.parentAccountId() == null
            ? null
            : account(mapper.findAccountById(row.parentAccountId()));
    AccountTypes accountType =
        Arrays.stream(AccountTypes.values())
            .filter(type -> type.getValue().getAccountTypeId() == row.accountTypeId())
            .findFirst()
            .orElseThrow(IllegalArgumentException::new);
    return Account.of(
        row.accountId(),
        accountType.getValue(),
        row.code(),
        row.name(),
        row.isActive(),
        row.createdTs(),
        parent);
  }

  private static CaptureChild capture(CaptureChildRow row) {
    return row == null
        ? null
        : new CaptureChild(
            row.transactionId(),
            row.reference(),
            row.quantity(),
            row.currencyId(),
            row.createdTs(),
            row.eventTypeId());
  }
}
