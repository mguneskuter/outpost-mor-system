package com.outpost.ledger.payment.repository.mybatis;

/** The PAYMENT transaction and its payment detail as the locking read returns them. */
public record PaymentTransactionRow(
    long transactionId,
    long currencyId,
    long merchantAccountId,
    long pspAccountId,
    long shopperCountryId,
    long grossQuantity,
    long netQuantity,
    long taxQuantity) {}
