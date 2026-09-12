package com.outpost.payment.repository.mybatis;

/** Persistence representation of a received PSP event. */
public record ReceivedPspEventRow(
    long merchantAccountId,
    long pspAccountId,
    long statusId,
    String reference,
    String originalReference,
    long eventCodeId,
    String payload) {}
