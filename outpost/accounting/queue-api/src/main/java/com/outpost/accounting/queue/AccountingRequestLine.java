package com.outpost.accounting.queue;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A requested order line and, when supplied, its full amount. */
public record AccountingRequestLine(String orderLineReference, @Nullable Long amount) {
  /** Creates a line with an optional full-line amount. */
  public AccountingRequestLine {
    if (orderLineReference == null || orderLineReference.isBlank()) {
      throw new IllegalArgumentException("orderLineReference must not be blank");
    }
    if (amount != null && amount < 0) {
      throw new IllegalArgumentException("amount must not be negative");
    }
  }

  /** Creates a line whose amount covers the full order line. */
  public AccountingRequestLine(String orderLineReference) {
    this(orderLineReference, null);
  }

  @Override
  public String orderLineReference() {
    return Objects.requireNonNull(orderLineReference);
  }
}
