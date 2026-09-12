package com.outpost.ledger.payment.repository;

import java.time.Instant;

/** Payment data needed to compare a repeated creation request. */
public record ExistingPayment(
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
