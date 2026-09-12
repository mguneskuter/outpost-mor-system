package com.outpost.accounting.queue.repository.mybatis;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Database row for one accounting request. */
public record AccountingRequestRow(
    Long queueId,
    Instant createdTs,
    @Nullable Instant doneTs,
    Boolean done,
    Long statusId,
    @Nullable Long resultId,
    Long typeId,
    String reference,
    String originalReference,
    Long accountId,
    @Nullable Long pspEventQueueId,
    @Nullable String idempotencyKey,
    @Nullable String merchantReference,
    @Nullable Boolean success,
    @Nullable Long amount,
    @Nullable Long currencyId,
    @Nullable String pspReference,
    @Nullable Long transactionId) {}
