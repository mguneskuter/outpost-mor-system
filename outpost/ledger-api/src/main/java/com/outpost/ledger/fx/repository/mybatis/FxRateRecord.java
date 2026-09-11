package com.outpost.ledger.fx.repository.mybatis;

import java.math.BigDecimal;
import java.time.LocalDate;

record FxRateRecord(
    long fxRateId,
    long baseCurrencyId,
    String baseCurrencyCode,
    int baseCurrencyExponent,
    long quoteCurrencyId,
    String quoteCurrencyCode,
    int quoteCurrencyExponent,
    LocalDate rateDate,
    BigDecimal rate,
    String source) {}
