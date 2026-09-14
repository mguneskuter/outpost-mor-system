package com.outpost.integration.psp;

import com.outpost.payment.common.Amount;

/** Request to create an order at a payment service provider. */
public record CreatePspOrderRequest(String pspCode, String paymentReference, Amount amount) {}
