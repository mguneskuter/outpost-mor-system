package com.outpost.ledger.payment.api;

/** Safe application-authored error response. */
public record PaymentError(String code) {}
