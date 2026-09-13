package com.outpost.payment.repository.mybatis;

import org.jspecify.annotations.Nullable;

/** A shopper as {@code shopper_detail} stores it: country and subdivision as codes. */
record ShopperDetail(
    @Nullable Long shopperId,
    String email,
    String fullName,
    String country,
    @Nullable String countrySubdivision,
    @Nullable String postalCode) {}
