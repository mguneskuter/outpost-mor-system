package com.outpost.backoffice.payment;

import java.time.Instant;

/**
 * One event the Ledger booked on a payment's transaction or on a capture or refund of it.
 *
 * @param quantity the transaction's amount in minor units
 */
public record PaymentEvent(
    long transactionEventId,
    String transactionType,
    String transactionReference,
    String currency,
    long quantity,
    String eventType,
    Instant occurredAt) {}
