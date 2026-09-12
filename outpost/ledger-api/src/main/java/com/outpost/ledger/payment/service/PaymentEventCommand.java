package com.outpost.ledger.payment.service;

/** Application command for recording a payment or refund lifecycle event. */
public record PaymentEventCommand(String paymentReference, String refundReference, String event) {}
