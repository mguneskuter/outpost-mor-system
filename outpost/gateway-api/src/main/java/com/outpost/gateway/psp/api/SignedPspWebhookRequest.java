package com.outpost.gateway.psp.api;

import com.outpost.integration.psp.simulator.PspConfiguration;

/** The PSP whose signature a PSP event notification matched. */
record SignedPspWebhookRequest(PspConfiguration psp) {}
