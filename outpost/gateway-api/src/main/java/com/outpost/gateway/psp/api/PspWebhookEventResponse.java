package com.outpost.gateway.psp.api;

/**
 * The result of a PSP event notification.
 *
 * @param code a {@link com.outpost.gateway.psp.service.PspWebhookResults} name
 */
public record PspWebhookEventResponse(String code) {}
