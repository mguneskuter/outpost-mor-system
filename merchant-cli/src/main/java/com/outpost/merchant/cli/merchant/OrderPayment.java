package com.outpost.merchant.cli.merchant;

/** The PSP's reference for an order's payment and the page where the shopper pays it. */
public record OrderPayment(String pspReference, String paymentLink) {}
