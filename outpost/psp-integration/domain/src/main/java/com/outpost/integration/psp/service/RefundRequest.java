package com.outpost.integration.psp.service;

import com.outpost.payment.common.Amount;

/** Request to refund an order at a payment service provider. */
public record RefundRequest(
    String pspCode, String pspReference, String refundReference, Amount amount) {}
