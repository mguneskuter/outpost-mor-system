package com.outpost.ledger.payment.repository;

/** Payment family data returned after locking the payment root. */
public record PaymentFamily(
    long transactionId,
    long currencyId,
    long merchantAccountId,
    long pspAccountId,
    long shopperCountryId,
    long grossQuantity,
    long netQuantity,
    long taxQuantity) {}
