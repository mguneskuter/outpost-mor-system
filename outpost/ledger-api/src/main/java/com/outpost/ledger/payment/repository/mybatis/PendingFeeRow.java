package com.outpost.ledger.payment.repository.mybatis;

/** Existing pending-fee entry data needed to append its reversal. */
public record PendingFeeRow(
    long fee, long currencyId, long merchantRegisterId, long platformRegisterId) {}
