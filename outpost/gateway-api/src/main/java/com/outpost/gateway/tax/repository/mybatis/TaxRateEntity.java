package com.outpost.gateway.tax.repository.mybatis;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Database entity for a tax rate. */
public record TaxRateEntity(
    String countryCode, @Nullable String subdivisionCode, BigDecimal rate) {}
