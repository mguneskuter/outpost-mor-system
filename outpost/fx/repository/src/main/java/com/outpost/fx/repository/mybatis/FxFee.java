package com.outpost.fx.repository.mybatis;

record FxFee(
    long fxFeeId,
    long baseCurrencyId,
    String baseCurrencyCode,
    int baseCurrencyExponent,
    long quoteCurrencyId,
    String quoteCurrencyCode,
    int quoteCurrencyExponent,
    int feeRateBps) {}
