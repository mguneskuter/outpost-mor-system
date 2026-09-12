package com.outpost.payment.repository.mybatis;

/** Persistence representation of a PSP event selected for processing. */
public record PspEventRow(
    long queueId,
    long merchantAccountId,
    String reference,
    String originalReference,
    long eventCodeId,
    String payload) {}
