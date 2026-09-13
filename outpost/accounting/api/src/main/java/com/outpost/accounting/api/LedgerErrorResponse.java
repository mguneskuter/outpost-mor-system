package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * The body of every Ledger error response: a stable code and, for an unexpected failure, the
 * identifier of the one log line that records it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LedgerErrorResponse(
    @JsonProperty("code") String code,
    @JsonProperty("correlation_id") @Nullable String correlationId) {
  public static final String INVALID_REQUEST = "INVALID_REQUEST";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
  public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
  public static final String FORBIDDEN = "FORBIDDEN";
  public static final String BODY_TOO_LARGE = "BODY_TOO_LARGE";

  /** An error response without a correlation identifier. */
  public static LedgerErrorResponse of(String code) {
    return new LedgerErrorResponse(code, null);
  }
}
