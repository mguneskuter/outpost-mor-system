package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Public result of payment creation. */
public record PaymentResponse(
    @JsonProperty("payment_reference") String paymentReference,
    @JsonProperty("created_at") Instant createdAt) {}
