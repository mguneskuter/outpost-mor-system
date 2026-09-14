package com.outpost.backoffice.payment;

import java.time.Instant;

/**
 * One line of a journal entry the Ledger posted for a payment's event.
 *
 * @param quantity the line's amount in minor units, debits positive
 */
public record PaymentJournalLine(
    long journalEntryId,
    String entryType,
    String eventType,
    String transactionReference,
    Instant postedAt,
    String accountCode,
    String accountName,
    String registerType,
    String currency,
    long quantity) {}
