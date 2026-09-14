package com.outpost.pspsimulator.refund;

/** A refund the simulator accepted or rejected on behalf of a PSP. */
public record Refund(
    String pspCode,
    String pspReference,
    String pspRefundReference,
    String refundReference,
    long amountMinor,
    String currencyCode,
    boolean accepted) {}
