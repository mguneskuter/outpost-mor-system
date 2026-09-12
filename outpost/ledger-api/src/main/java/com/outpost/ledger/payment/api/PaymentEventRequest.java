package com.outpost.ledger.payment.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON request for recording a payment lifecycle event. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PaymentEventRequest(
    @JsonProperty("payment_reference") String paymentReference,
    @JsonProperty("event") String event) {}
