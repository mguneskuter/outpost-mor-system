package com.outpost.ledger.payment.repository.mybatis;

import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentFamily;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PendingFee;
import java.time.Instant;
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
  public ExistingPaymentRow findByReference(String r) {
    return mapper.findByReference(r);
  }

  @Override
  public AccountRow findAccount(String c) {
    return mapper.findAccount(c);
  }

  @Override
  public AccountRow findAccountById(long i) {
    return mapper.findAccountById(i);
  }

  @Override
  public FeeRow findFee(long a, long c) {
    return mapper.findFee(a, c);
  }

  @Override
  public Long findTaxAuthority(long c) {
    return mapper.findTaxAuthority(c);
  }

  @Override
  public Long findPlatform() {
    return mapper.findPlatform();
  }

  @Override
  public Long findPendingRegister(long a) {
    return mapper.findPendingRegister(a);
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
  public long insertEntry(long i, Instant t) {
    return mapper.insertEntry(i, t);
  }

  @Override
  public long insertLine(long e, long r, long c, long q) {
    return mapper.insertLine(e, r, c, q);
  }

  @Override
  public PaymentFamily findPaymentFamilyForUpdate(String r) {
    PaymentFamilyRow row = mapper.findPaymentFamilyForUpdate(r);
    return row == null ? null : new PaymentFamily(row.transactionId(), row.currencyId());
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
  public long insertFeeReleaseEntry(long i, long t, Instant at) {
    return mapper.insertFeeReleaseEntry(i, t, at);
  }
}
