package com.outpost.ledger.fx.repository.mybatis;

record FxFeeRecord(
    long fxFeeId,
    long baseCurrencyId,
    String baseCurrencyCode,
    int baseCurrencyExponent,
    long quoteCurrencyId,
    String quoteCurrencyCode,
    int quoteCurrencyExponent,
    int feeRateBps) {}
