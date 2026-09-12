package com.outpost.accounting.queue;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Immutable input for adding accounting work to the queue. */
public final class SubmitAccountingRequestCommand {
  private final AccountingRequestTypes type;
  private final String reference;
  private final String originalReference;
  private final long accountId;
  private final @Nullable Long pspEventQueueId;
  private final @Nullable String idempotencyKey;
  private final @Nullable String merchantReference;
  private final @Nullable Boolean success;
  private final @Nullable Long amount;
  private final @Nullable Long currencyId;
  private final @Nullable String pspReference;
  private final List<AccountingRequestLine> lines;

  /** Creates a fully described request. */
  public SubmitAccountingRequestCommand(
      AccountingRequestTypes type,
      String reference,
      String originalReference,
      long accountId,
      @Nullable Long pspEventQueueId,
      @Nullable String idempotencyKey,
      @Nullable String merchantReference,
      @Nullable Boolean success,
      @Nullable Long amount,
      @Nullable Long currencyId,
      @Nullable String pspReference,
      List<AccountingRequestLine> lines) {
    this.type = Objects.requireNonNull(type, "type");
    this.reference = requiredText(reference, "reference");
    this.originalReference = requiredText(originalReference, "originalReference");
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    this.accountId = accountId;
    this.pspEventQueueId = positiveOrNull(pspEventQueueId, "pspEventQueueId");
    this.idempotencyKey = optionalText(idempotencyKey, "idempotencyKey");
    this.merchantReference = optionalText(merchantReference, "merchantReference");
    this.success = success;
    this.amount = nonNegativeOrNull(amount, "amount");
    this.currencyId = positiveOrNull(currencyId, "currencyId");
    this.pspReference = optionalText(pspReference, "pspReference");
    this.lines = copyLines(lines);
  }

  /** Creates a request with the common idempotency fields. */
  public SubmitAccountingRequestCommand(
      AccountingRequestTypes type,
      String reference,
      String originalReference,
      long accountId,
      @Nullable String idempotencyKey,
      List<AccountingRequestLine> lines) {
    this(
        type,
        reference,
        originalReference,
        accountId,
        null,
        idempotencyKey,
        null,
        null,
        null,
        null,
        null,
        lines);
  }

  public AccountingRequestTypes getType() {
    return type;
  }

  public String getReference() {
    return reference;
  }

  public String getOriginalReference() {
    return originalReference;
  }

  public long getAccountId() {
    return accountId;
  }

  public @Nullable Long getPspEventQueueId() {
    return pspEventQueueId;
  }

  public @Nullable String getIdempotencyKey() {
    return idempotencyKey;
  }

  public @Nullable String getMerchantReference() {
    return merchantReference;
  }

  public @Nullable Boolean getSuccess() {
    return success;
  }

  public @Nullable Long getAmount() {
    return amount;
  }

  public @Nullable Long getCurrencyId() {
    return currencyId;
  }

  public @Nullable String getPspReference() {
    return pspReference;
  }

  public List<AccountingRequestLine> getLines() {
    return lines;
  }

  private static String requiredText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  private static @Nullable String optionalText(@Nullable String value, String name) {
    if (value != null && value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  private static @Nullable Long positiveOrNull(@Nullable Long value, String name) {
    if (value != null && value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  private static @Nullable Long nonNegativeOrNull(@Nullable Long value, String name) {
    if (value != null && value < 0) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
    return value;
  }

  private static List<AccountingRequestLine> copyLines(List<AccountingRequestLine> lines) {
    Objects.requireNonNull(lines, "lines");
    Set<String> references = new HashSet<>();
    for (AccountingRequestLine line : lines) {
      if (line == null || !references.add(line.orderLineReference())) {
        throw new IllegalArgumentException("lines must contain unique non-null references");
      }
    }
    return List.copyOf(lines);
  }
}
