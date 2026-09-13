package com.outpost.ledger.payment.repository;

/** The PAYMENT transaction and its payment detail, read under the transaction's row lock. */
public record PaymentTransaction(
    long transactionId,
    long currencyId,
    long merchantAccountId,
    long pspAccountId,
    long shopperCountryId,
    long grossQuantity,
    long netQuantity,
    long taxQuantity) {}
