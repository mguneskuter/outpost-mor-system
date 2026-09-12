package com.outpost.worker.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configures accounting request polling. */
@Validated
@ConfigurationProperties("outpost.worker.accounting")
public record AccountingWorkerProperties(int workerCount, Duration pollInterval) {
  /** Validates the configured worker count and polling interval. */
  public AccountingWorkerProperties {
    if (workerCount <= 0) {
      throw new IllegalArgumentException("workerCount must be positive");
    }
    if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
      throw new IllegalArgumentException("pollInterval must be positive");
    }
  }
}
