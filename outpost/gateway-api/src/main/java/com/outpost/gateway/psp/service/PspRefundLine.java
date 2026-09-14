package com.outpost.gateway.psp.service;

import java.math.BigDecimal;

/**
 * One refunded order line as the PSP echoes it on a REFUND event.
 *
 * @param taxRate the rate the PSP was given for the line
 * @param netAmount the line's net, in minor units
 * @param grossAmount the line's net plus tax, in minor units
 */
public record PspRefundLine(
    String orderLineReference, BigDecimal taxRate, long netAmount, long grossAmount) {}
