package com.outpost.ledger.payment.repository;

import java.time.Instant;

/** Persisted refund child and its latest lifecycle event. */
public record RefundChild(
    long transactionId,
    long paymentTransactionId,
    String reference,
    long quantity,
    long currencyId,
    long netQuantity,
    long taxQuantity,
    Instant createdTs,
    Long eventTypeId) {}
