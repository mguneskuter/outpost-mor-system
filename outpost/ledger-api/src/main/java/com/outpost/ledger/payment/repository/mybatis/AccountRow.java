package com.outpost.ledger.payment.repository.mybatis;

import java.time.Instant;

/** Account data needed to resolve a payment participant without building a domain tree. */
public record AccountRow(
    long accountId,
    long accountTypeId,
    String code,
    String name,
    boolean isActive,
    Instant createdTs,
    Long parentAccountId,
    Long parentTypeId,
    String parentCode,
    String parentName,
    Boolean parentActive,
    Instant parentCreatedTs) {}
