package com.outpost.pspsimulator.configuration;

import com.outpost.pspsimulator.psp.PspAccount;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The simulator's runtime configuration: its PSP accounts, the URLs it reports and posts to, and
 * the delays before each webhook event.
 */
@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
    String publicUrl, String outpostBaseUrl, DelaySettings delays, List<PspAccount> psps) {

  /** Validates the configured URLs, delays, and PSP accounts. */
  public SimulatorProperties {
    requireNonBlank(publicUrl, "simulator.public-url must not be blank");
    requireNonBlank(outpostBaseUrl, "simulator.outpost-base-url must not be blank");
    Objects.requireNonNull(delays, "delays");
    Objects.requireNonNull(psps, "psps");
    if (psps.isEmpty()) {
      throw new IllegalArgumentException("simulator.psps must declare at least one PSP");
    }
  }

  /** The delays before each webhook event, all zero to report immediately. */
  public record DelaySettings(
      Duration authorisationMin, Duration authorisationMax, Duration capture, Duration refund) {

    /** Validates that all webhook delays are present and non-negative. */
    public DelaySettings {
      requireNonNull(authorisationMin, "authorisationMin");
      requireNonNull(authorisationMax, "authorisationMax");
      requireNonNull(capture, "capture");
      requireNonNull(refund, "refund");
      requireNonNegative(authorisationMin, "authorisationMin");
      requireNonNegative(authorisationMax, "authorisationMax");
      requireNonNegative(capture, "capture");
      requireNonNegative(refund, "refund");
      if (authorisationMax.compareTo(authorisationMin) < 0) {
        throw new IllegalArgumentException(
            "authorisationMax must not be earlier than authorisationMin");
      }
    }

    private static void requireNonNegative(Duration duration, String name) {
      if (duration.isNegative()) {
        throw new IllegalArgumentException(name + " must not be negative");
      }
    }
  }

  private static void requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void requireNonNull(Object value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must be configured");
    }
  }
}
