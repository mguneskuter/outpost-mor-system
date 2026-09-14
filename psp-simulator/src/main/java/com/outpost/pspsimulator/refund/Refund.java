package com.outpost.pspsimulator.refund;

/**
 * A refund the simulator acknowledged on behalf of a PSP; {@code succeeded} is what its REFUND
 * webhook reports.
 */
public record Refund(
    String pspCode,
    String pspReference,
    String pspRefundReference,
    String refundReference,
    long amountMinor,
    String currencyCode,
    boolean succeeded) {}
