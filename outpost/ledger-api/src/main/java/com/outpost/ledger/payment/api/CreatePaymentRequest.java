package com.outpost.ledger.payment.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON request for creating a payment. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreatePaymentRequest(
    @JsonProperty("payment_reference") String paymentReference,
    @JsonProperty("merchant_code") String merchantCode,
    @JsonProperty("psp_code") String pspCode,
    @JsonProperty("shopper_country") String shopperCountry,
    @JsonProperty("shopper_country_subdivision") String shopperCountrySubdivision,
    @JsonProperty("net_amount") Long netAmount,
    @JsonProperty("tax_amount") Long taxAmount,
    @JsonProperty("gross_amount") Long grossAmount,
    @JsonProperty("currency") String currency) {}
