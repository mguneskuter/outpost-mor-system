package com.outpost.worker.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configures accounting request polling. */
@Validated
@ConfigurationProperties("outpost.worker.accounting")
public record AccountingWorkerProperties(
    @Min(value = 1, message = "must be between 1 and 64")
        @Max(value = 64, message = "must be between 1 and 64")
        int workerCount,
    @NotNull(message = "must be set")
        @DurationMin(millis = 10, message = "must be between 10ms and 1m")
        @DurationMax(minutes = 1, message = "must be between 10ms and 1m")
        Duration pollInterval) {}
