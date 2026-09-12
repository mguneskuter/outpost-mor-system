package com.outpost.worker.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configures the Worker's signed HTTP client to Ledger's payment routes. */
@Validated
@ConfigurationProperties("outpost.worker.ledger")
public record LedgerWorkerProperties(
    String baseUrl, String hmacSecret, Duration connectTimeout, Duration readTimeout) {
  /** Rejects a Worker boot without an externally supplied Ledger host and signing secret. */
  public LedgerWorkerProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("outpost.worker.ledger.base-url must be configured");
    }
    if (hmacSecret == null || hmacSecret.isBlank()) {
      throw new IllegalArgumentException("outpost.worker.ledger.hmac-secret must be configured");
    }
    if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
      throw new IllegalArgumentException("outpost.worker.ledger.connect-timeout must be positive");
    }
    if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
      throw new IllegalArgumentException("outpost.worker.ledger.read-timeout must be positive");
    }
  }
}
