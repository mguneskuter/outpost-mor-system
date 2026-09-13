package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON request stating whether a PSP capture succeeded or failed. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CaptureRequest(
    @JsonProperty("payment_reference") String paymentReference,
    @JsonProperty("capture_reference") String captureReference,
    @JsonProperty("success") Boolean success,
    @JsonProperty("amount") Long amount,
    @JsonProperty("currency") String currency) {}
