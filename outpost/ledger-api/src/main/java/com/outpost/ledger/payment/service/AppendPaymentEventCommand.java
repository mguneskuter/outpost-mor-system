package com.outpost.ledger.payment.service;

/** Application command for appending a payment or refund lifecycle event. */
public record AppendPaymentEventCommand(
    String paymentReference, String refundReference, String event) {}
