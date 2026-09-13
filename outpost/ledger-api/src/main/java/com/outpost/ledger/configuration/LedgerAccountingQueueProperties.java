package com.outpost.ledger.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings of the Ledger's accounting queue and transaction lock. */
@Validated
@ConfigurationProperties("outpost.ledger.accounting-queue")
public record LedgerAccountingQueueProperties(
    @Min(value = 1, message = "must be at least 1") @Max(value = 64, message = "must be at most 64")
        int workerCount,
    @NotNull(message = "must be set")
        @DurationMin(millis = 10, message = "must be between 10ms and 1m")
        @DurationMax(minutes = 1, message = "must be between 10ms and 1m")
        Duration pollInterval,
    @NotNull(message = "must be set")
        @DurationMin(seconds = 1, message = "must be between 1s and 1h")
        @DurationMax(hours = 1, message = "must be between 1s and 1h")
        Duration transactionLockLease) {}
