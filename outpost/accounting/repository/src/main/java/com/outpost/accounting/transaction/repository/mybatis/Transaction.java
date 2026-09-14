package com.outpost.accounting.transaction.repository.mybatis;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A transaction as {@code transaction} stores it: type and currency as codes, parent as an id. */
record Transaction(
    @Nullable Long transactionId,
    String transactionType,
    @Nullable Long parentTransactionId,
    long accountId,
    String reference,
    String currencyCode,
    long amount,
    @Nullable Instant createdAt) {}
