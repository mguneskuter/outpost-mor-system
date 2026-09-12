package com.outpost.payment.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** MyBatis mapper for orders and their priced lines. */
@RegisteredMapper
public interface PaymentOrderMapper {
  /** Finds the order paid by this payment reference. */
  @Nullable OrderRow findOrder(String paymentReference);

  /** Finds an order's priced lines, in sequence order. */
  List<OrderItemRow> findOrderItems(long orderId);

  /** Finds the PSP this payment reference was routed to. */
  @Nullable PspRoutingRow findPspRouting(String paymentReference);
}
