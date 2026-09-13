package com.outpost.ledger.payment.repository;

import java.time.Instant;

/**
 * A transaction as its insert stored it.
 *
 * @param createdAt the time of the database transaction that stored the row
 */
public record StoredTransaction(long transactionId, Instant createdAt) {}
