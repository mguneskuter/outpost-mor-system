package com.outpost.ledger.report.repository;

import org.jspecify.annotations.Nullable;

/**
 * The sum of one register's line quantities in one currency over a period; a positive quantity is a
 * debit. A null {@code currencyCode} means no line was posted to the register in the period, and
 * the quantity is then zero.
 */
public record RegisterBalance(
    String accountCode,
    String accountTypeCode,
    String registerTypeCode,
    @Nullable String currencyCode,
    long quantity) {}
