package com.outpost.ledger.payment.repository.mybatis;

/** Payment family root locked for a lifecycle mutation. */
public record PaymentFamilyRow(
    long transactionId,
    long currencyId,
    long merchantAccountId,
    long pspAccountId,
    long shopperCountryId,
    long grossQuantity,
    long netQuantity,
    long taxQuantity) {}
