package com.outpost.merchant.webhook.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record IncomingWebhookResponse(
    boolean success,
    @JsonProperty("merchant_reference") String merchantReference,
    @JsonProperty("outpost_reference") String outpostReference,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("event_timestamp") Instant eventTimestamp,
    @Nullable String reason) {}
