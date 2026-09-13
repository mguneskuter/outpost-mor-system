package com.outpost.worker.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Timeout settings for PSP calls made by Worker. */
@Validated
@ConfigurationProperties("outpost.worker.psp-client")
public record PspClientProperties(
    @NotNull(message = "must be set")
        @DurationMin(millis = 100, message = "must be between 100ms and 30s")
        @DurationMax(seconds = 30, message = "must be between 100ms and 30s")
        Duration connectTimeout,
    @NotNull(message = "must be set")
        @DurationMin(millis = 100, message = "must be between 100ms and 5m")
        @DurationMax(minutes = 5, message = "must be between 100ms and 5m")
        Duration readTimeout) {
  /** Validates that the connection timeout does not exceed the read timeout. */
  public PspClientProperties {
    if (connectTimeout != null
        && readTimeout != null
        && connectTimeout.compareTo(readTimeout) > 0) {
      throw new IllegalArgumentException(
          "outpost.worker.psp-client.connect-timeout must not exceed "
              + "outpost.worker.psp-client.read-timeout");
    }
  }
}
