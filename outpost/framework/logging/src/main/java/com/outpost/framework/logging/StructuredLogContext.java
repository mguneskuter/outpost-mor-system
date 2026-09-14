package com.outpost.framework.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
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
  public static Scope open(LogField field, String value) {
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

  /** Opens a context scope for safe typed fields. */
  public static Scope open(StructuredLogField... fields) {
    Objects.requireNonNull(fields, "fields");
    List<Scope> scopes = new ArrayList<>();
    try {
      for (StructuredLogField field : fields) {
        scopes.add(open(field.field(), field.value()));
      }
      return new Scope(scopes);
    } catch (RuntimeException exception) {
      for (int index = scopes.size() - 1; index >= 0; index--) {
        scopes.get(index).close();
      }
      throw exception;
    }
  }

  /** Restores the previous context value when the scope closes. */
  public static final class Scope implements AutoCloseable {

    private final @Nullable String key;
    private final @Nullable String previousValue;
    private final List<Scope> scopes;

    private Scope(String key, String previousValue) {
      this.key = key;
      this.previousValue = previousValue;
      scopes = List.of();
    }

    private Scope(List<Scope> scopes) {
      key = null;
      previousValue = null;
      this.scopes = List.copyOf(scopes);
    }

    @Override
    public void close() {
      if (!scopes.isEmpty()) {
        for (int index = scopes.size() - 1; index >= 0; index--) {
          scopes.get(index).close();
        }
        return;
      }
      if (key == null) {
        return;
      }
      if (previousValue == null) {
        MDC.remove(key);
      } else {
        MDC.put(key, previousValue);
      }
    }
  }
}
