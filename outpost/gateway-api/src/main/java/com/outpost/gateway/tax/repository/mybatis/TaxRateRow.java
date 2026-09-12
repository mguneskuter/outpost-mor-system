package com.outpost.gateway.tax.repository.mybatis;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Persistence representation of a tax rate. */
public record TaxRateRow(String countryCode, @Nullable String subdivisionCode, BigDecimal rate) {}
