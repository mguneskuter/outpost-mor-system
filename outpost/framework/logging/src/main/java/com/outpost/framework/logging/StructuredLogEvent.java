package com.outpost.framework.logging;

import java.util.Objects;
import org.slf4j.Logger;

/** Emits one safe structured event without exposing throwable or arbitrary-key overloads. */
public final class StructuredLogEvent {

  private StructuredLogEvent() {}

  /**
   * Emits an informational event with one typed application field.
   *
   * @param logger the logger supplied by the owning module
   * @param message a safe, application-authored message
   * @param field the field descriptor owned by the owning module
   * @param value the safe value for that field
   */
  public static void info(Logger logger, String message, LogFields field, String value) {
    Objects.requireNonNull(logger, "logger");
    Objects.requireNonNull(message, "message");
    try (StructuredLogContext.Scope ignored = StructuredLogContext.open(field, value)) {
      logger.info(message);
    }
  }
}
