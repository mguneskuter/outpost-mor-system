package com.outpost.ledger.payment.repository.mybatis;

/** Merchant fee configuration row. */
public record FeeRow(
    long merchantFeeConfigurationId,
    long accountId,
    long currencyId,
    long feeModeId,
    int feeRateBps,
    Long feeFixed) {}
