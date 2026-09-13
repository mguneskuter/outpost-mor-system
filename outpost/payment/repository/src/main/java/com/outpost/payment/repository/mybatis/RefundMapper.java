package com.outpost.payment.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.refund.Refund;

/** MyBatis statements for merchant refunds. */
@RegisteredMapper
public interface RefundMapper {
  /** Inserts a refund and returns it as stored. */
  Refund insertRefund(Refund refund);
}
