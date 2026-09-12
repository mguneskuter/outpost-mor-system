package com.outpost.ledger.report.repository.mybatis;

/** MyBatis projection of an account balance grouped by line currency. */
public record BalanceRow(String accountCode, String accountName, String currency, long amount) {}
