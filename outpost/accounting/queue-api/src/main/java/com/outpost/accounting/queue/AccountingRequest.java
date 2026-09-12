package com.outpost.accounting.queue;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Immutable view of an accounting request returned by the queue. */
public final class AccountingRequest {
  private final long queueId;
  private final Instant createdAt;
  private final @Nullable Instant doneAt;
  private final boolean done;
  private final AccountingRequestStatuses status;
  private final @Nullable AccountingRequestResults result;
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
  private final @Nullable Long transactionId;
  private final List<AccountingRequestLine> lines;

  AccountingRequest(
      long queueId,
      Instant createdAt,
      @Nullable Instant doneAt,
      boolean done,
      AccountingRequestStatuses status,
      @Nullable AccountingRequestResults result,
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
      @Nullable Long transactionId,
      List<AccountingRequestLine> lines) {
    this.queueId = queueId;
    this.createdAt = createdAt;
    this.doneAt = doneAt;
    this.done = done;
    this.status = status;
    this.result = result;
    this.type = type;
    this.reference = reference;
    this.originalReference = originalReference;
    this.accountId = accountId;
    this.pspEventQueueId = pspEventQueueId;
    this.idempotencyKey = idempotencyKey;
    this.merchantReference = merchantReference;
    this.success = success;
    this.amount = amount;
    this.currencyId = currencyId;
    this.pspReference = pspReference;
    this.transactionId = transactionId;
    this.lines = List.copyOf(lines);
  }

  public long getQueueId() {
    return queueId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public @Nullable Instant getDoneAt() {
    return doneAt;
  }

  public boolean isDone() {
    return done;
  }

  public AccountingRequestStatuses getStatus() {
    return status;
  }

  public @Nullable AccountingRequestResults getResult() {
    return result;
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

  public @Nullable Long getTransactionId() {
    return transactionId;
  }

  public List<AccountingRequestLine> getLines() {
    return lines;
  }
}
