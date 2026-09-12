package com.outpost.ledger.payment.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON request for recording a PSP capture outcome. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CaptureRequest(
    @JsonProperty("payment_reference") String paymentReference,
    @JsonProperty("capture_reference") String captureReference,
    @JsonProperty("success") Boolean success,
    @JsonProperty("amount") Long amount,
    @JsonProperty("currency") String currency) {}
