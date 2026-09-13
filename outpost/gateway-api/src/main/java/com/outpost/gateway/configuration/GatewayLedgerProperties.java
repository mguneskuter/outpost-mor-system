package com.outpost.gateway.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings for signed calls from Gateway to Ledger. */
@Validated
@ConfigurationProperties("outpost.gateway.ledger")
public record GatewayLedgerProperties(
    @NotBlank(message = "must be set") @URL(message = "must be a well-formed URL") String baseUrl,
    @NotBlank(message = "must be set") String hmacSecret,
    @NotNull(message = "must be set")
        @DurationMin(millis = 100, message = "must be between 100ms and 30s")
        @DurationMax(seconds = 30, message = "must be between 100ms and 30s")
        Duration connectTimeout,
    @NotNull(message = "must be set")
        @DurationMin(millis = 100, message = "must be between 100ms and 5m")
        @DurationMax(minutes = 5, message = "must be between 100ms and 5m")
        Duration readTimeout) {
  /** Validates that the connection timeout does not exceed the read timeout. */
  public GatewayLedgerProperties {
    if (connectTimeout != null
        && readTimeout != null
        && connectTimeout.compareTo(readTimeout) > 0) {
      throw new IllegalArgumentException(
          "outpost.gateway.ledger.connect-timeout must not exceed "
              + "outpost.gateway.ledger.read-timeout");
    }
  }
}
