package com.outpost.gateway.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings of Gateway's accounting queue and its delivery to the Ledger. */
@Validated
@ConfigurationProperties("outpost.gateway.accounting-queue")
public record GatewayAccountingQueueProperties(
    @Min(value = 1, message = "must be between 1 and 16")
        @Max(value = 16, message = "must be between 1 and 16")
        int workerCount,
    @NotNull(message = "must be set")
        @DurationMin(millis = 10, message = "must be between 10ms and 1m")
        @DurationMax(minutes = 1, message = "must be between 10ms and 1m")
        Duration pollInterval,
    @NotNull(message = "must be set")
        @DurationMin(millis = 10, message = "must be between 10ms and 10m")
        @DurationMax(minutes = 10, message = "must be between 10ms and 10m")
        Duration retryDelay,
    @Min(value = 1, message = "must be between 1 and 1000")
        @Max(value = 1000, message = "must be between 1 and 1000")
        int maxAttempts) {}
