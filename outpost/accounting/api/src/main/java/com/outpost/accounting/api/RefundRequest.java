package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON request for reserving a refund. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RefundRequest(
    @JsonProperty("payment_reference") String paymentReference,
    @JsonProperty("refund_reference") String refundReference,
    @JsonProperty("net_amount") Long netAmount,
    @JsonProperty("tax_amount") Long taxAmount,
    @JsonProperty("currency") String currency) {}
