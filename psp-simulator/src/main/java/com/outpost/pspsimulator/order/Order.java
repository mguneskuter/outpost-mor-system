package com.outpost.pspsimulator.order;

/** An order the simulator accepted on behalf of a PSP. */
public record Order(
    String pspCode,
    long pspReference,
    String paymentReference,
    long amountMinor,
    String currencyCode,
    OrderStatuses status) {}
