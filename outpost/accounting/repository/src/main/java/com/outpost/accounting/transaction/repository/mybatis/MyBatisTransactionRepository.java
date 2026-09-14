package com.outpost.accounting.transaction.repository.mybatis;

import com.outpost.account.Account;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.TransactionTypes.TransactionType;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores transactions in {@code transaction}, {@code payment_detail}, {@code refund_detail}, and
 * {@code transaction_event}; the accounts of a stored transaction are read through {@code
 * accounts}.
 */
public final class MyBatisTransactionRepository implements TransactionRepository {
  private final TransactionMapper mapper;
  private final TransactionTemplate transactionTemplate;
  private final AccountRepository accounts;

  /**
   * Creates a repository over a Spring-managed {@code sqlSession}, so its statements join the
   * transactions {@code transactionManager} opens.
   */
  public MyBatisTransactionRepository(
      SqlSession sqlSession,
      PlatformTransactionManager transactionManager,
      AccountRepository accounts) {
    this.mapper = sqlSession.getMapper(TransactionMapper.class);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.accounts = accounts;
  }

  @Override
  public Optional<com.outpost.accounting.transaction.PaymentDetail> insertPaymentDetail(
      com.outpost.accounting.transaction.PaymentDetail paymentDetail) {
    com.outpost.accounting.transaction.Transaction payment = paymentDetail.getPaymentTransaction();
    // The transaction and its detail commit or roll back together only inside this Spring
    // transaction: outside one, each mapper statement commits alone on the pooled connection.
    return Objects.requireNonNull(
        transactionTemplate.execute(
            status -> {
              Transaction stored = mapper.insertTransaction(toStored(payment, null));
              if (stored == null) {
                return Optional.<com.outpost.accounting.transaction.PaymentDetail>empty();
              }
              mapper.insertPaymentDetail(
                  new PaymentDetail(
                      storedId(stored),
                      stored.accountId(),
                      stored.reference(),
                      stored.currencyCode(),
                      stored.amount(),
                      storedCreatedAt(stored),
                      paymentDetail.getShopperCountry().getIsoCode(),
                      paymentDetail
                          .getShopperCountrySubdivision()
                          .map(CountrySubdivision::getCode)
                          .orElse(null),
                      paymentDetail.getPspAccount().getAccountId(),
                      paymentDetail.getNetAmount().quantity(),
                      paymentDetail.getTaxAmount().quantity()));
              return Optional.of(
                  new com.outpost.accounting.transaction.PaymentDetail(
                      toTransaction(stored, payment.getMerchantAccount(), null),
                      paymentDetail.getShopperCountry(),
                      paymentDetail.getShopperCountrySubdivision().orElse(null),
                      paymentDetail.getPspAccount(),
                      paymentDetail.getNetAmount(),
                      paymentDetail.getTaxAmount()));
            }));
  }

  @Override
  public Optional<com.outpost.accounting.transaction.PaymentDetail> findPaymentDetailByReference(
      String reference) {
    return Optional.ofNullable(mapper.findPaymentDetailByReference(reference))
        .map(this::toPaymentDetail);
  }

  @Override
  public Optional<com.outpost.accounting.transaction.PaymentDetail>
      findPaymentDetailByReferenceForUpdate(String reference) {
    return Optional.ofNullable(mapper.findPaymentDetailByReferenceForUpdate(reference))
        .map(this::toPaymentDetail);
  }

  @Override
  public Optional<com.outpost.accounting.transaction.Transaction> insertTransaction(
      com.outpost.accounting.transaction.Transaction transaction) {
    com.outpost.accounting.transaction.Transaction payment =
        transaction
            .getParentTransaction()
            .orElseThrow(() -> new IllegalArgumentException("Transaction has no payment parent"));
    return Optional.ofNullable(mapper.insertTransaction(toStored(transaction, storedId(payment))))
        .map(
            stored -> toTransaction(stored, transaction.getMerchantAccount(), storedCopy(payment)));
  }

  @Override
  public List<com.outpost.accounting.transaction.TransactionEvent> findTransactionEvents(
      com.outpost.accounting.transaction.Transaction transaction) {
    return mapper.findTransactionEvents(storedId(transaction)).stream()
        .map(event -> toTransactionEvent(event, transaction))
        .toList();
  }

  @Override
  public Optional<com.outpost.accounting.transaction.TransactionEvent> insertTransactionEvent(
      com.outpost.accounting.transaction.Transaction transaction,
      TransactionEventType transactionEventType) {
    return Optional.ofNullable(
            mapper.insertTransactionEvent(storedId(transaction), transactionEventType.getCode()))
        .map(event -> toTransactionEvent(event, transaction));
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException when the payment has more than one capture, or its capture does
   *     not have exactly one event
   */
  @Override
  public Optional<com.outpost.accounting.transaction.TransactionEvent>
      findCaptureTransactionEventByPayment(com.outpost.accounting.transaction.Transaction payment) {
    List<Transaction> captures =
        mapper.findChildTransactions(
            storedId(payment), TransactionTypes.CAPTURE.getValue().getCode());
    if (captures.isEmpty()) {
      return Optional.empty();
    }
    if (captures.size() > 1) {
      throw new IllegalStateException("Payment has more than one capture");
    }
    Transaction stored = captures.getFirst();
    com.outpost.accounting.transaction.Transaction capture =
        toTransaction(stored, account(stored.accountId()), storedCopy(payment));
    List<TransactionEvent> events = mapper.findTransactionEvents(storedId(stored));
    if (events.size() != 1) {
      throw new IllegalStateException("Capture does not have exactly one event");
    }
    return Optional.of(toTransactionEvent(events.getFirst(), capture));
  }

  @Override
  public Optional<com.outpost.accounting.transaction.RefundDetail> insertRefundDetail(
      com.outpost.accounting.transaction.RefundDetail refundDetail) {
    com.outpost.accounting.transaction.Transaction refund = refundDetail.getRefundTransaction();
    com.outpost.accounting.transaction.Transaction payment =
        refund
            .getParentTransaction()
            .orElseThrow(() -> new IllegalArgumentException("Refund has no payment parent"));
    long paymentId = storedId(payment);
    return Objects.requireNonNull(
        transactionTemplate.execute(
            status -> {
              Transaction stored = mapper.insertTransaction(toStored(refund, paymentId));
              if (stored == null) {
                return Optional.<com.outpost.accounting.transaction.RefundDetail>empty();
              }
              mapper.insertRefundDetail(
                  new RefundDetail(
                      storedId(stored),
                      paymentId,
                      stored.accountId(),
                      stored.reference(),
                      stored.currencyCode(),
                      stored.amount(),
                      storedCreatedAt(stored),
                      refundDetail.getNetAmount().quantity(),
                      refundDetail.getTaxAmount().quantity()));
              return Optional.of(
                  new com.outpost.accounting.transaction.RefundDetail(
                      toTransaction(stored, refund.getMerchantAccount(), storedCopy(payment)),
                      refundDetail.getNetAmount(),
                      refundDetail.getTaxAmount()));
            }));
  }

  @Override
  public Optional<com.outpost.accounting.transaction.RefundDetail> findRefundDetailByReference(
      String reference) {
    return Optional.ofNullable(mapper.findRefundDetailByReference(reference))
        .map(
            refund -> {
              Transaction payment = mapper.findTransactionById(refund.parentTransactionId());
              if (payment == null) {
                throw new IllegalStateException("Stored refund's payment is missing");
              }
              return toRefundDetail(
                  refund, toTransaction(payment, account(payment.accountId()), null));
            });
  }

  @Override
  public List<com.outpost.accounting.transaction.RefundDetail> findRefundDetailsByPayment(
      com.outpost.accounting.transaction.Transaction payment) {
    com.outpost.accounting.transaction.Transaction parent = storedCopy(payment);
    return mapper.findRefundDetails(storedId(payment)).stream()
        .map(refund -> toRefundDetail(refund, parent))
        .toList();
  }

  private com.outpost.accounting.transaction.PaymentDetail toPaymentDetail(PaymentDetail stored) {
    Currency currency = currency(stored.currencyCode());
    Country country =
        Countries.fromIsoCode(stored.shopperCountry())
            .orElseThrow(() -> new IllegalStateException("Stored country is not supported"));
    String subdivisionCode = stored.shopperCountrySubdivision();
    CountrySubdivision subdivision =
        subdivisionCode == null
            ? null
            : CountrySubdivisions.fromCode(country, subdivisionCode)
                .orElseThrow(
                    () -> new IllegalStateException("Stored subdivision is not supported"));
    com.outpost.accounting.transaction.Transaction payment =
        com.outpost.accounting.transaction.Transaction.of(
            stored.transactionId(),
            TransactionTypes.PAYMENT.getValue(),
            account(stored.accountId()),
            stored.reference(),
            new Amount(currency, stored.amount()),
            stored.createdAt());
    return new com.outpost.accounting.transaction.PaymentDetail(
        payment,
        country,
        subdivision,
        account(stored.pspAccountId()),
        new Amount(currency, stored.netAmount()),
        new Amount(currency, stored.taxAmount()));
  }

  private com.outpost.accounting.transaction.RefundDetail toRefundDetail(
      RefundDetail stored, com.outpost.accounting.transaction.Transaction payment) {
    Currency currency = currency(stored.currencyCode());
    com.outpost.accounting.transaction.Transaction refund =
        com.outpost.accounting.transaction.Transaction.childOf(
            payment,
            stored.transactionId(),
            TransactionTypes.REFUND.getValue(),
            account(stored.accountId()),
            stored.reference(),
            new Amount(currency, stored.amount()),
            stored.createdAt());
    return new com.outpost.accounting.transaction.RefundDetail(
        refund, new Amount(currency, stored.netAmount()), new Amount(currency, stored.taxAmount()));
  }

  private static com.outpost.accounting.transaction.Transaction toTransaction(
      Transaction stored,
      Account merchantAccount,
      com.outpost.accounting.transaction.@Nullable Transaction parent) {
    TransactionType transactionType =
        TransactionTypes.fromCode(stored.transactionType())
            .orElseThrow(
                () -> new IllegalStateException("Stored transaction type is not supported"));
    Amount amount = new Amount(currency(stored.currencyCode()), stored.amount());
    return parent == null
        ? com.outpost.accounting.transaction.Transaction.of(
            storedId(stored),
            transactionType,
            merchantAccount,
            stored.reference(),
            amount,
            storedCreatedAt(stored))
        : com.outpost.accounting.transaction.Transaction.childOf(
            parent,
            storedId(stored),
            transactionType,
            merchantAccount,
            stored.reference(),
            amount,
            storedCreatedAt(stored));
  }

  private static com.outpost.accounting.transaction.TransactionEvent toTransactionEvent(
      TransactionEvent stored, com.outpost.accounting.transaction.Transaction transaction) {
    return new com.outpost.accounting.transaction.TransactionEvent(
        stored.transactionEventId(),
        transaction,
        TransactionEventTypes.fromCode(stored.transactionEventType())
            .orElseThrow(() -> new IllegalStateException("Stored event type is not supported")),
        stored.occurredAt());
  }

  private static Transaction toStored(
      com.outpost.accounting.transaction.Transaction transaction,
      @Nullable Long parentTransactionId) {
    return new Transaction(
        null,
        transaction.getTransactionType().getCode(),
        parentTransactionId,
        transaction.getMerchantAccount().getAccountId(),
        transaction.getReference(),
        transaction.getAmount().currency().getCurrencyCode(),
        transaction.getAmount().quantity(),
        null);
  }

  /**
   * Returns a copy of a stored transaction without children, for a child built here to attach to.
   */
  private static com.outpost.accounting.transaction.Transaction storedCopy(
      com.outpost.accounting.transaction.Transaction transaction) {
    return com.outpost.accounting.transaction.Transaction.of(
        storedId(transaction),
        transaction.getTransactionType(),
        transaction.getMerchantAccount(),
        transaction.getReference(),
        transaction.getAmount(),
        transaction
            .getCreatedAt()
            .orElseThrow(() -> new IllegalArgumentException("Transaction is not stored")));
  }

  private static long storedId(com.outpost.accounting.transaction.Transaction transaction) {
    return transaction
        .getTransactionId()
        .orElseThrow(() -> new IllegalArgumentException("Transaction is not stored"));
  }

  private static long storedId(Transaction stored) {
    return Objects.requireNonNull(stored.transactionId(), "transactionId");
  }

  private static Instant storedCreatedAt(Transaction stored) {
    return Objects.requireNonNull(stored.createdAt(), "createdAt");
  }

  private Account account(long accountId) {
    return accounts
        .findAccountById(accountId)
        .orElseThrow(() -> new IllegalStateException("Stored account is missing"));
  }

  private static Currency currency(String currencyCode) {
    return Currencies.fromCurrencyCode(currencyCode)
        .orElseThrow(() -> new IllegalStateException("Stored currency is not supported"));
  }
}
