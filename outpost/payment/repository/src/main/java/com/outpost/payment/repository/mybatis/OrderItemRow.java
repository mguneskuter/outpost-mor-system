package com.outpost.payment.repository.mybatis;

import java.math.BigDecimal;

/** MyBatis projection of one {@code order_item}. */
public record OrderItemRow(
    long orderItemId,
    int sequence,
    long productTypeId,
    String orderLineReference,
    String merchantLineReference,
    long netAmount,
    long taxAmount,
    BigDecimal taxRate) {}
