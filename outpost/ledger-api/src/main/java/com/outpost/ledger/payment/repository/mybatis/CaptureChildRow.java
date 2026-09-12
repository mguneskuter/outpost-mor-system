package com.outpost.ledger.payment.repository.mybatis;

import java.time.Instant;

/** MyBatis projection of a capture child and its outcome. */
public record CaptureChildRow(
    long transactionId,
    String reference,
    long quantity,
    long currencyId,
    Instant createdTs,
    Long eventTypeId) {}
