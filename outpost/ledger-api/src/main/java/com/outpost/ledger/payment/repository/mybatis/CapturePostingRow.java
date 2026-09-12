package com.outpost.ledger.payment.repository.mybatis;

/** MyBatis projection of the counterparties in a successful capture entry. */
public record CapturePostingRow(
    long pspAccountId,
    long pspRegisterId,
    long taxAuthorityAccountId,
    long taxRegisterId,
    long merchantAccountId,
    long merchantRegisterId,
    long currencyId) {}
