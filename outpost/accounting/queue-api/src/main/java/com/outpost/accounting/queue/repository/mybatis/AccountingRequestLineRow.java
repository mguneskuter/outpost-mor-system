package com.outpost.accounting.queue.repository.mybatis;

import org.jspecify.annotations.Nullable;

/** Database row for one requested accounting line. */
public record AccountingRequestLineRow(
    long queueId, String orderLineReference, @Nullable Long amount) {}
