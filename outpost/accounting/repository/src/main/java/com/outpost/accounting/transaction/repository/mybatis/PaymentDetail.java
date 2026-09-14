package com.outpost.accounting.transaction.repository.mybatis;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A payment as {@code transaction} and {@code payment_detail} store it. */
record PaymentDetail(
    long transactionId,
    long accountId,
    String reference,
    String currencyCode,
    long amount,
    Instant createdAt,
    String shopperCountry,
    @Nullable String shopperCountrySubdivision,
    long pspAccountId,
    long netAmount,
    long taxAmount) {}
