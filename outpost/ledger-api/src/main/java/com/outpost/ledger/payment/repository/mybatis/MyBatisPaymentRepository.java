package com.outpost.ledger.payment.repository.mybatis;

import com.outpost.ledger.payment.repository.PaymentRepository;
import java.time.Instant;
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
}
