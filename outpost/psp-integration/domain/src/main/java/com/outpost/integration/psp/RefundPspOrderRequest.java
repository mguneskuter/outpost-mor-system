package com.outpost.integration.psp;

/** Request to refund an order in full at a payment service provider. */
public record RefundPspOrderRequest(String pspCode, String pspReference, String refundReference) {}
