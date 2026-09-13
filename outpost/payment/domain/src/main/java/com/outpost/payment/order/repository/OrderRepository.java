package com.outpost.payment.order.repository;

import com.outpost.payment.ShopperDetail;
import com.outpost.payment.order.Order;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Stores payment orders with their lines, and reads them back as stored. */
public interface OrderRepository {
  /** Finds the order a merchant created under an idempotency key, with its lines. */
  Optional<Order> findOrderByIdempotencyKey(long accountId, String idempotencyKey);

  /** Finds an order by its reference, scoped to the merchant that owns it, with its lines. */
  Optional<Order> findOrderByOrderReference(long accountId, String orderReference);

  /** Finds the order paid by this payment reference, with its lines. */
  Optional<Order> findOrderByPaymentReference(String paymentReference);

  /** Finds the PSP this payment reference was routed to. */
  Optional<PspRouting> findPspRoutingByPaymentReference(String paymentReference);

  /**
   * Stores an unsaved order and its lines for a shopper, as one unit that commits or stores
   * nothing. A shopper whose email is already stored keeps the details it was first stored with,
   * and the order is stored under that shopper.
   *
   * @return the stored order, carrying the ids the store assigned; empty when the merchant already
   *     used the order's idempotency key, in which case nothing is stored
   */
  Optional<Order> insertOrder(ShopperDetail shopper, Order order);

  /**
   * Stores the PSP reference and payment link on the order paid by {@code paymentReference}. An
   * order that already has a PSP reference keeps it.
   */
  void updateOrderPspReferenceAndPaymentLink(
      String paymentReference, String pspReference, String paymentLink);

  /**
   * The PSP code and the PSP's own reference for a payment. {@code pspReference} is absent until
   * the PSP has created its order.
   */
  record PspRouting(String pspCode, @Nullable String pspReference) {}
}
