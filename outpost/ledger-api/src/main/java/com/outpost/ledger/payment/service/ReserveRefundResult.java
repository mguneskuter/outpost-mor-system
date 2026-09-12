package com.outpost.ledger.payment.service;

import java.time.Instant;

/** Result of reserving a refund amount. */
public record ReserveRefundResult(String refundReference, Instant createdAt) {}
