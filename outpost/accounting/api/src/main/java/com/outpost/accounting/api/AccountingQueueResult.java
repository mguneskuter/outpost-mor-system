package com.outpost.accounting.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * What the Ledger did with one accounting request, with the identifying fields of the request it
 * answers. A refused request's fields are echoed as sent, so any of them may be absent.
 *
 * @param resultCode {@link #ACCEPTED}, or the name of the {@link AccountingQueueErrorTypes} that
 *     refused the request
 * @param reason why the request was refused; absent when it was accepted
 */
public record AccountingQueueResult(
    @JsonProperty("success") boolean success,
    @JsonProperty("result_code") String resultCode,
    @JsonProperty("reason") @Nullable String reason,
    @JsonProperty("type") @Nullable AccountingQueueRequestTypes type,
    @JsonProperty("original_reference") @Nullable String originalReference,
    @JsonProperty("merchant_reference") @Nullable String merchantReference,
    @JsonProperty("psp_reference") @Nullable String pspReference,
    @JsonProperty("psp_code") @Nullable String pspCode,
    @JsonProperty("merchant_code") @Nullable String merchantCode,
    @JsonProperty("refund_reference") @Nullable String refundReference) {
  /** The result code of a request the Ledger accepted and queued. */
  public static final String ACCEPTED = "ACCEPTED";

  /** Answers a request the Ledger accepted and queued. */
  public static AccountingQueueResult accepted(AccountingQueueRequest request) {
    return new AccountingQueueResult(
        true,
        ACCEPTED,
        null,
        request.type(),
        request.originalReference(),
        request.merchantReference(),
        request.pspReference(),
        request.pspCode(),
        request.merchantCode(),
        request.refundReference());
  }

  /** Answers {@code request}, refused for {@code error}. */
  public static AccountingQueueResult refused(
      AccountingQueueRequest request, AccountingQueueErrorTypes error) {
    return new AccountingQueueResult(
        false,
        error.name(),
        error.getReason(),
        request.type(),
        request.originalReference(),
        request.merchantReference(),
        request.pspReference(),
        request.pspCode(),
        request.merchantCode(),
        request.refundReference());
  }

  /** Answers a refusal for {@code error} when the body named no request. */
  public static AccountingQueueResult refused(AccountingQueueErrorTypes error) {
    return new AccountingQueueResult(
        false, error.name(), error.getReason(), null, null, null, null, null, null, null);
  }
}
