package com.outpost.payment.repository;

import com.outpost.payment.order.Order;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Persistence boundary for reading a placed order and its PSP routing, by payment reference. */
public interface PaymentOrderRepository {
  /** Finds the order paid by this payment reference, with its priced lines. */
  Optional<Order> findByPaymentReference(String paymentReference);

  /** Finds the PSP this payment reference was routed to. */
  Optional<PspRouting> findPspRoutingByPaymentReference(String paymentReference);

  /**
   * The PSP code and the PSP's own reference for a payment. {@code pspReference} is absent until
   * the PSP confirms the payment.
   */
  record PspRouting(String pspCode, @Nullable String pspReference) {}
}
