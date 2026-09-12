package com.outpost.ledger.payment.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Public result of recording a capture outcome. */
public record CaptureResponse(
    @JsonProperty("capture_reference") String captureReference,
    @JsonProperty("created_at") Instant createdAt) {}
