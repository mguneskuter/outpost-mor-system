package com.outpost.accounting.transaction.repository.mybatis;

import java.time.Instant;

/** A refund as {@code transaction} and {@code refund_detail} store it. */
record RefundDetail(
    long transactionId,
    long parentTransactionId,
    long accountId,
    String reference,
    String currencyCode,
    long amount,
    Instant createdAt,
    long netAmount,
    long taxAmount) {}
