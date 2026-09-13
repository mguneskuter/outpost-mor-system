package com.outpost.payment.repository.mybatis;

import com.outpost.payment.order.repository.OrderRepository.PspRouting;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis statements for payment orders, their lines, and their shoppers. Package-private, like the
 * stored forms it returns: a MyBatis proxy of a public interface is defined in another module and
 * cannot reach package-private result types.
 */
interface OrderMapper {
  /** Finds the order a merchant created under an idempotency key. */
  @Nullable Order findOrderByIdempotencyKey(
      @Param("accountId") long accountId, @Param("idempotencyKey") String idempotencyKey);

  /** Finds an order by its reference, scoped to the merchant that owns it. */
  @Nullable Order findOrderByOrderReference(
      @Param("accountId") long accountId, @Param("orderReference") String orderReference);

  /** Finds the order paid by this payment reference. */
  @Nullable Order findOrderByPaymentReference(@Param("paymentReference") String paymentReference);

  /** Finds an order's lines in the order they were inserted. */
  List<OrderItem> findOrderItems(@Param("orderId") long orderId);

  /** Finds the PSP this payment reference was routed to. */
  @Nullable PspRouting findPspRoutingByPaymentReference(
      @Param("paymentReference") String paymentReference);

  /** Finds the shopper with this email. */
  @Nullable ShopperDetail findShopperDetailByEmail(@Param("email") String email);

  /** Inserts a shopper and returns it as stored, or null when a shopper with its email exists. */
  @Nullable ShopperDetail insertShopperDetail(ShopperDetail shopperDetail);

  /** Inserts an order and returns it as stored, or null when its idempotency key is used. */
  @Nullable Order insertOrder(Order order);

  /** Inserts one order line and returns it as stored. */
  OrderItem insertOrderItem(OrderItem orderItem);

  /** Stores PSP facts on an order that has no PSP reference yet. */
  int updateOrderPspReferenceAndPaymentLink(
      @Param("paymentReference") String paymentReference,
      @Param("pspReference") String pspReference,
      @Param("paymentLink") String paymentLink);
}
