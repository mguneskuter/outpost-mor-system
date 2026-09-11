package com.outpost.integration.psp.service;

import com.outpost.payment.common.Amount;

/** Request to create an order at a payment service provider. */
public record CreateOrderRequest(String pspCode, String paymentReference, Amount amount) {}
