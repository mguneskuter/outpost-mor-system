package com.outpost.account.repository.mybatis;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** An account as {@code account} stores it: account type as a code, parent as an id. */
record Account(
    long accountId,
    String accountType,
    @Nullable Long parentAccountId,
    String code,
    String name,
    boolean active,
    Instant createdAt) {}
