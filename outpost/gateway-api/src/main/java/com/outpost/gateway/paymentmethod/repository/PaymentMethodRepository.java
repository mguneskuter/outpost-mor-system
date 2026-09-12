package com.outpost.gateway.paymentmethod.repository;

import java.util.List;

/** Persistence boundary for a merchant's enabled payment methods. */
public interface PaymentMethodRepository {
  /** Lists the PSPs enabled for a merchant. */
  List<PaymentMethod> findEnabled(long merchantAccountId);

  /** One PSP enabled for a merchant. */
  record PaymentMethod(String pspCode, String name) {}
}
