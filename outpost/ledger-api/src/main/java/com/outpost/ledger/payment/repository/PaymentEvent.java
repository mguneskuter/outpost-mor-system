package com.outpost.ledger.payment.repository;

import java.time.Instant;

/** Recorded payment event data needed by lifecycle processing. */
public record PaymentEvent(
    long transactionEventId, long transactionEventTypeId, Instant occurredAt) {}
