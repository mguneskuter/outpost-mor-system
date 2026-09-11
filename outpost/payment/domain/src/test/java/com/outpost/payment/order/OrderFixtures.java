package com.outpost.payment.order;

import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class OrderFixtures {
  static final Instant CREATED = Instant.parse("2026-02-01T00:00:00Z");

  private OrderFixtures() {}

  static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  static OrderItem item(long orderItemId, String orderLineReference, String merchantLineReference) {
    return new OrderItem(
        orderItemId,
        1,
        ProductTypes.DIGITAL_GOODS.getValue(),
        orderLineReference,
        merchantLineReference,
        eur(1000L),
        eur(210L),
        new BigDecimal("0.21"));
  }

  static Order order(long orderId, List<OrderItem> items, Amount netAmount, Amount taxAmount) {
    return new Order(
        orderId,
        "order-ref-" + orderId,
        "merchant-ref-" + orderId,
        100L,
        200L,
        netAmount,
        taxAmount,
        netAmount.plus(taxAmount),
        "idempotency-" + orderId,
        CREATED,
        items);
  }
}
