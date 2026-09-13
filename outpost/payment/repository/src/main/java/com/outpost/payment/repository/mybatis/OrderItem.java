package com.outpost.payment.repository.mybatis;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * An order line as {@code order_item} stores it: product type as a code, amounts as minor units.
 */
record OrderItem(
    @Nullable Long orderItemId,
    long orderId,
    String productType,
    String orderLineReference,
    String merchantLineReference,
    long netAmount,
    long taxAmount,
    BigDecimal taxRate) {}
