package com.outpost.payment.repository.mybatis;

/** Persistence representation of payment accounts. */
public record PaymentAccountsRow(long merchantAccountId, long pspAccountId) {}
