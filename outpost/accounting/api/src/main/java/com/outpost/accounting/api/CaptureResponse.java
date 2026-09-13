package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Public result of a stored capture. */
public record CaptureResponse(
    @JsonProperty("capture_reference") String captureReference,
    @JsonProperty("created_at") Instant createdAt) {}
