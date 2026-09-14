package com.outpost.backoffice.payment;

import java.math.BigDecimal;

/**
 * One line of a stored order and whether a refund that has not failed covers it.
 *
 * @param netAmount the line's net in minor units of the order's currency
 * @param taxAmount the line's tax in minor units of the order's currency
 * @param taxRate the rate applied when the order was priced
 * @param refunded whether a refund that has not failed claims the line
 */
public record OrderLine(
    String orderLineReference,
    String merchantLineReference,
    String productType,
    long netAmount,
    long taxAmount,
    BigDecimal taxRate,
    boolean refunded) {}
