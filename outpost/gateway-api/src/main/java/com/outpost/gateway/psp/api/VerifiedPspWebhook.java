package com.outpost.gateway.psp.api;

import com.outpost.integration.psp.simulator.repository.PspConfiguration;

/**
 * A PSP event notification whose signature matched the addressed PSP.
 *
 * @param payload the notification body exactly as signed
 */
record VerifiedPspWebhook(PspConfiguration psp, String payload) {}
