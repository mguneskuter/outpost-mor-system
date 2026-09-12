package com.outpost.payment.repository.mybatis;

import org.jspecify.annotations.Nullable;

/** MyBatis projection of a payment's PSP routing. */
public record PspRoutingRow(String pspCode, @Nullable String pspReference) {}
