package com.outpost.ledger.payment.repository.mybatis;

/** MyBatis projection of a register and its owning account type. */
public record RegisterRow(
    long registerId, long accountId, long accountTypeId, long registerTypeId) {}
