package com.outpost.common.iso.repository;

/** Database record for one country. */
public record CountryRecord(long countryId, String isoCode, String name) {}
