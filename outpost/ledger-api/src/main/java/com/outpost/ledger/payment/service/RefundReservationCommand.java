package com.outpost.ledger.payment.service;

/** Application command for reserving a refund amount. */
public record RefundReservationCommand(
    String paymentReference,
    String refundReference,
    Long netAmount,
    Long taxAmount,
    String currency) {}
