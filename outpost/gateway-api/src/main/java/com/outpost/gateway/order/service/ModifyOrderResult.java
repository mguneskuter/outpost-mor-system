package com.outpost.gateway.order.service;

/** Outcome of submitting an order modification request. */
public record ModifyOrderResult(String refundReference, String status) {}
