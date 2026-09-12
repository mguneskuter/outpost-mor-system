package com.outpost.worker.accounting.repository.mybatis;

/** MyBatis projection of the net and tax amounts already refunded for one order line. */
public record RefundedTotalRow(long net, long tax) {}
