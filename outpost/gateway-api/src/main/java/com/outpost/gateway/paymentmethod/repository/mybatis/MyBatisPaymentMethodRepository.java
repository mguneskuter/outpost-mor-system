package com.outpost.gateway.paymentmethod.repository.mybatis;

import com.outpost.gateway.paymentmethod.repository.PaymentMethodRepository;
import java.util.List;

/** MyBatis persistence adapter for a merchant's enabled payment methods. */
public final class MyBatisPaymentMethodRepository implements PaymentMethodRepository {
  private final PaymentMethodMapper mapper;

  /** Creates an adapter backed by the payment method mapper. */
  public MyBatisPaymentMethodRepository(PaymentMethodMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<PaymentMethod> findEnabled(long merchantAccountId) {
    return mapper.findEnabled(merchantAccountId).stream()
        .map(row -> new PaymentMethod(row.pspCode(), row.name()))
        .toList();
  }
}
