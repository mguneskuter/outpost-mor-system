package com.outpost.ledger.payment.repository.mybatis;

import java.time.Instant;

/** Persisted payment fingerprint and creation timestamp. */
public record ExistingPaymentRow(
    long transactionId,
    long accountId,
    String reference,
    long quantity,
    long currencyId,
    Instant createdTs,
    long pspAccountId,
    long shopperCountryId,
    Long shopperCountrySubdivisionId,
    long netQuantity,
    long taxQuantity) {}
