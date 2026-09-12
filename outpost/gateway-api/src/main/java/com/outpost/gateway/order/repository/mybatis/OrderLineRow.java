package com.outpost.gateway.order.repository.mybatis;

/** MyBatis projection of one order line. */
public record OrderLineRow(
    int sequence,
    long productTypeId,
    String orderLineReference,
    String merchantLineReference,
    long netAmount,
    long taxAmount,
    String taxRate) {}
