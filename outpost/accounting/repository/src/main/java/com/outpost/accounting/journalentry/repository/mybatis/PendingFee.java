package com.outpost.accounting.journalentry.repository.mybatis;

/** A FEE_PENDING entry's fee and its two lines' registers, as the journal tables store them. */
record PendingFee(
    String currencyCode,
    long fee,
    long merchantPendingFeeRegisterId,
    String merchantRegisterType,
    long merchantAccountId,
    long platformPendingFeeRegisterId,
    String platformRegisterType,
    long platformAccountId) {}
