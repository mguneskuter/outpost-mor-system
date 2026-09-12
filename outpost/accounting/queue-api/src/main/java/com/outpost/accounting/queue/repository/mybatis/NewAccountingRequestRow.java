package com.outpost.accounting.queue.repository.mybatis;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Insert values for one accounting request. */
public record NewAccountingRequestRow(
    long typeId,
    String reference,
    String originalReference,
    long accountId,
    Instant createdTs,
    @Nullable Long pspEventQueueId,
    @Nullable String idempotencyKey,
    @Nullable String merchantReference,
    @Nullable Boolean success,
    @Nullable Long amount,
    @Nullable Long currencyId,
    @Nullable String pspReference) {}
