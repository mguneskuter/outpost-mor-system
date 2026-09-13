package com.outpost.gateway.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Timeout settings for PSP calls made by Gateway. */
@Validated
@ConfigurationProperties("outpost.gateway.psp-client")
public record GatewayPspClientProperties(
    @NotNull(message = "must be set")
        @DurationMin(millis = 100, message = "must be between 100ms and 30s")
        @DurationMax(seconds = 30, message = "must be between 100ms and 30s")
        Duration connectTimeout,
    @NotNull(message = "must be set")
        @DurationMin(millis = 100, message = "must be between 100ms and 5m")
        @DurationMax(minutes = 5, message = "must be between 100ms and 5m")
        Duration readTimeout) {
  /** Validates that the connection timeout does not exceed the read timeout. */
  public GatewayPspClientProperties {
    if (connectTimeout != null
        && readTimeout != null
        && connectTimeout.compareTo(readTimeout) > 0) {
      throw new IllegalArgumentException(
          "outpost.gateway.psp-client.connect-timeout must not exceed "
              + "outpost.gateway.psp-client.read-timeout");
    }
  }
}
