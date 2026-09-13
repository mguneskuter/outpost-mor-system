package com.outpost.gateway.psp.service;

import com.outpost.payment.PspEventCodes;

/**
 * A PSP event notification about one payment.
 *
 * @param pspReference the PSP's reference for the payment the event concerns
 * @param eventReference the PSP's reference for this event, unique per PSP account
 */
public record PspWebhookEvent(
    String pspCode,
    String pspReference,
    String paymentReference,
    PspEventCodes eventCode,
    String eventReference) {}
