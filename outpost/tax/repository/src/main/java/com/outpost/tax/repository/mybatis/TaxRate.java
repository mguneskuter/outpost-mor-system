package com.outpost.tax.repository.mybatis;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Stored tax rate, with its jurisdiction and product type as codes. */
record TaxRate(
    String countryCode,
    @Nullable String subdivisionCode,
    @Nullable String productTypeCode,
    BigDecimal rate) {}
