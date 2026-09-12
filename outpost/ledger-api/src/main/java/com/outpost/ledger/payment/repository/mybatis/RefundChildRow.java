package com.outpost.ledger.payment.repository.mybatis;

import java.time.Instant;

/** MyBatis projection of a refund child and its latest lifecycle event. */
public record RefundChildRow(
    long transactionId,
    long paymentTransactionId,
    String reference,
    long quantity,
    long currencyId,
    long netQuantity,
    long taxQuantity,
    Instant createdTs,
    Long eventTypeId) {}
