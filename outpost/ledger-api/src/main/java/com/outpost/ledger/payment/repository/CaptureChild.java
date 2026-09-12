package com.outpost.ledger.payment.repository;

import java.time.Instant;

/** Persisted capture child and its single outcome event. */
public record CaptureChild(
    long transactionId,
    String reference,
    long quantity,
    long currencyId,
    Instant createdTs,
    Long eventTypeId) {}
