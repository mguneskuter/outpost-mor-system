package com.outpost.ledger.payment.repository.mybatis;

import java.time.Instant;

/** Persisted payment lifecycle event in transaction-event order. */
public record PaymentEventRow(
    long transactionEventId, long transactionEventTypeId, Instant occurredAt) {}
