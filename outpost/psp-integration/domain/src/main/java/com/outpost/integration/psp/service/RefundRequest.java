package com.outpost.integration.psp.service;

/** Request to refund an order in full at a payment service provider. */
public record RefundRequest(String pspCode, String pspReference, String refundReference) {}
