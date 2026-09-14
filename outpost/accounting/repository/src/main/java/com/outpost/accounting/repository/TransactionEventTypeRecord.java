package com.outpost.accounting.repository;

/** Database row for a transaction event type. */
public record TransactionEventTypeRecord(
    long transactionEventTypeId, String code, boolean requiresJournalEntry) {}
