package com.outpost.framework.logging;

import java.util.Objects;
import org.slf4j.Logger;

/** Wraps an SLF4J logger to emit events with safe typed fields. */
public final class StructuredLogger {
  private final Logger logger;

  /** Creates a structured logger backed by the supplied SLF4J logger. */
  public StructuredLogger(Logger logger) {
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  /** Emits an informational event with safe typed application fields. */
  public void info(String message, StructuredLogField... fields) {
    try (StructuredLogContext.Scope scope = StructuredLogContext.open(fields)) {
      logger.info(message);
    }
  }

  /** Emits a warning event with safe typed application fields. */
  public void warn(String message, StructuredLogField... fields) {
    try (StructuredLogContext.Scope scope = StructuredLogContext.open(fields)) {
      logger.warn(message);
    }
  }

  /** Emits a warning event with safe typed application fields and its cause. */
  public void warn(String message, Throwable exception, StructuredLogField... fields) {
    Objects.requireNonNull(exception, "exception");
    try (StructuredLogContext.Scope scope = StructuredLogContext.open(fields)) {
      logger.warn(message, exception);
    }
  }

  /** Emits an error event with safe typed application fields. */
  public void error(String message, StructuredLogField... fields) {
    try (StructuredLogContext.Scope scope = StructuredLogContext.open(fields)) {
      logger.error(message);
    }
  }

  /** Emits an error event with safe typed application fields and its cause. */
  public void error(String message, Throwable exception, StructuredLogField... fields) {
    Objects.requireNonNull(exception, "exception");
    try (StructuredLogContext.Scope scope = StructuredLogContext.open(fields)) {
      logger.error(message, exception);
    }
  }
}
