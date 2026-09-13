package com.outpost.framework.logging;

import java.util.Objects;

/** A safe value paired with a field owned by the emitting module. */
public record StructuredLogField(LogFields field, String value) {
  /** Creates one safe field value. */
  public StructuredLogField {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(value, "value");
  }
}
