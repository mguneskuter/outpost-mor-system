package com.outpost.gateway.order.client;

import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.Amount;
import org.jspecify.annotations.Nullable;

/** Payment facts sent from Gateway to Ledger. */
public record LedgerPayment(
    String paymentReference,
    String merchantCode,
    String pspCode,
    Country shopperCountry,
    @Nullable CountrySubdivision shopperCountrySubdivision,
    Amount netAmount,
    Amount taxAmount,
    Amount grossAmount) {}
