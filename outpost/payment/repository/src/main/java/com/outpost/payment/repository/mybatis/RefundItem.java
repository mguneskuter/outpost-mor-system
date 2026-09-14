package com.outpost.payment.repository.mybatis;

import java.math.BigDecimal;

/** A refund item as {@code refund_item} stores it, joined to the order line it claims. */
record RefundItem(
    long refundItemId,
    long refundId,
    boolean refundFailed,
    long orderItemId,
    long orderId,
    String productType,
    String orderLineReference,
    String merchantLineReference,
    long netAmount,
    long taxAmount,
    BigDecimal taxRate) {
  /** Returns the claimed order line as {@code order_item} stores it. */
  OrderItem orderItem() {
    return new OrderItem(
        orderItemId,
        orderId,
        productType,
        orderLineReference,
        merchantLineReference,
        netAmount,
        taxAmount,
        taxRate);
  }
}
