package com.outpost.gateway.order.service;

/** Outcome of submitting an order modification request. */
public record OrderModificationResult(String refundReference, String status) {}
