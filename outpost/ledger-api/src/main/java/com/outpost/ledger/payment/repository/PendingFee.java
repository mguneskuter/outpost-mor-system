package com.outpost.ledger.payment.repository;

/** Pending-fee data needed to reverse a payment fee. */
public record PendingFee(
    long fee, long currencyId, long merchantRegisterId, long platformRegisterId) {}
