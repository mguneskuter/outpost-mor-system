package com.outpost.ledger.report.repository;

/** One persisted balance grouped by account and line currency. */
public record BalanceLine(String accountCode, String accountName, String currency, long amount) {}
