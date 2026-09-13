package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.accounting.api.json.AmountDeserializer;
import com.outpost.accounting.api.json.AmountSerializer;
import com.outpost.accounting.api.json.CountryIsoCodeDeserializer;
import com.outpost.accounting.api.json.CountryIsoCodeSerializer;
import com.outpost.accounting.api.json.CountrySubdivisionCodeDeserializer;
import com.outpost.accounting.api.json.CountrySubdivisionCodeSerializer;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.Amount;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/** One request to change a payment's accounting, sent by Gateway to the Ledger. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AccountingQueueRequest(
    @JsonProperty("type") AccountingQueueRequestTypes type,
    @JsonProperty("original_reference") String originalReference,
    @JsonProperty("merchant_reference") String merchantReference,
    @JsonProperty("psp_code") String pspCode,
    @JsonProperty("psp_reference") String pspReference,
    @JsonProperty("success") @Nullable Boolean success,
    @JsonProperty("refund_reference") @Nullable String refundReference,
    @JsonProperty("merchant_code") @Nullable String merchantCode,
    @JsonProperty("shopper_country")
        @JsonSerialize(using = CountryIsoCodeSerializer.class)
        @JsonDeserialize(using = CountryIsoCodeDeserializer.class)
        @Nullable Country shopperCountry,
    @JsonProperty("shopper_country_subdivision")
        @JsonSerialize(using = CountrySubdivisionCodeSerializer.class)
        @JsonDeserialize(using = CountrySubdivisionCodeDeserializer.class)
        @Nullable CountrySubdivision shopperCountrySubdivision,
    @JsonProperty("net_amount")
        @JsonSerialize(using = AmountSerializer.class)
        @JsonDeserialize(using = AmountDeserializer.class)
        @Nullable Amount netAmount,
    @JsonProperty("tax_amount")
        @JsonSerialize(using = AmountSerializer.class)
        @JsonDeserialize(using = AmountDeserializer.class)
        @Nullable Amount taxAmount,
    @JsonProperty("gross_amount")
        @JsonSerialize(using = AmountSerializer.class)
        @JsonDeserialize(using = AmountDeserializer.class)
        @Nullable Amount grossAmount) {}
