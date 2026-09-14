package com.outpost.accounting.transaction.repository.mybatis;

import java.time.Instant;

/** An event as {@code transaction_event} stores it, its type as a code. */
record TransactionEvent(long transactionEventId, String transactionEventType, Instant occurredAt) {}
