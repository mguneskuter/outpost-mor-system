package com.outpost.common.iso.repository;

/** Database record for one country subdivision. */
public record CountrySubdivisionRecord(
    long countrySubdivisionId, long countryId, String code, String name) {}
