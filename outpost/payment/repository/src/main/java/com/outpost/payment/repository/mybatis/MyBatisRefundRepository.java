package com.outpost.payment.repository.mybatis;

import com.outpost.payment.refund.Refund;
import com.outpost.payment.refund.repository.RefundRepository;

/** Stores merchant refunds in {@code merchant_refund}. */
public final class MyBatisRefundRepository implements RefundRepository {
  private final RefundMapper mapper;

  /** Creates a repository over the refund mapper. */
  public MyBatisRefundRepository(RefundMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Refund insertRefund(Refund refund) {
    return mapper.insertRefund(refund);
  }
}
