package com.outpost.common.iso.repository;

/** Database record for one currency. */
public record CurrencyRecord(long currencyId, String currencyCode, int exponent) {}
