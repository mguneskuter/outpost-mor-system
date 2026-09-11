package com.outpost.merchant.webhook.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record IncomingWebhookRequest(
    @JsonProperty("merchant_reference") String merchantReference,
    @JsonProperty("outpost_reference") String outpostReference,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("event_timestamp") Instant eventTimestamp,
    boolean success,
    @JsonProperty("reason") @Nullable String reason) {}
