package com.outpost.ledger.payment.repository;

/** The registers and accounts used by a payment's successful capture entry. */
public record CapturePosting(
    long pspAccountId,
    long pspRegisterId,
    long taxAuthorityAccountId,
    long taxRegisterId,
    long merchantAccountId,
    long merchantRegisterId,
    long currencyId) {}
