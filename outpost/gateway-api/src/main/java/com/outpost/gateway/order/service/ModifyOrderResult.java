package com.outpost.gateway.order.service;

/** Outcome of a refund the PSP accepted. */
public record ModifyOrderResult(String refundReference) {}
