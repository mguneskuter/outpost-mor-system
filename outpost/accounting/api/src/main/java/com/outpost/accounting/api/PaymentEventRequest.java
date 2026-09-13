package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON request for recording a payment or refund lifecycle event. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PaymentEventRequest(
    @JsonProperty("payment_reference") String paymentReference,
    @JsonProperty("refund_reference") String refundReference,
    @JsonProperty("event") String event) {}
