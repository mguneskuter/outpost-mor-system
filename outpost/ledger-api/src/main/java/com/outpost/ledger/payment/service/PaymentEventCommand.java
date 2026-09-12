package com.outpost.ledger.payment.service;

/** Application command for recording a payment lifecycle event. */
public record PaymentEventCommand(String paymentReference, String event) {}
