package com.outpost.framework.logging;

import java.util.Objects;
import org.slf4j.MDC;

/** Adds one safe, typed field to the current structured-log context. */
public final class StructuredLogContext {

  private StructuredLogContext() {}

  /**
   * Opens a context scope for one typed field.
   *
   * @param field the field descriptor owned by the calling module
   * @param value the already-safe application-authored value
   * @return a scope that restores the previous field value when closed
   */
  public static Scope open(LogFields field, String value) {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(value, "value");
    String key = Objects.requireNonNull(field.getJsonKey(), "field.getJsonKey()");
    if (key.isBlank()) {
      throw new IllegalArgumentException("field.getJsonKey() must not be blank");
    }
    String previousValue = MDC.get(key);
    MDC.put(key, value);
    return new Scope(key, previousValue);
  }

  /** Restores the previous context value when the scope closes. */
  public static final class Scope implements AutoCloseable {

    private final String key;
    private final String previousValue;

    private Scope(String key, String previousValue) {
      this.key = key;
      this.previousValue = previousValue;
    }

    @Override
    public void close() {
      if (previousValue == null) {
        MDC.remove(key);
      } else {
        MDC.put(key, previousValue);
      }
    }
  }
}
