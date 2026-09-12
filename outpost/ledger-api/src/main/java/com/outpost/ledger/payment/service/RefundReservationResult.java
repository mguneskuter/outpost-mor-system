package com.outpost.ledger.payment.service;

import java.time.Instant;

/** Result of reserving a refund amount. */
public record RefundReservationResult(String refundReference, Instant createdAt) {}
