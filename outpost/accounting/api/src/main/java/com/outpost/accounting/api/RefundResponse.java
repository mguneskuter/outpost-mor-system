package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Public result of reserving a refund. */
public record RefundResponse(
    @JsonProperty("refund_reference") String refundReference,
    @JsonProperty("created_at") Instant createdAt) {}
