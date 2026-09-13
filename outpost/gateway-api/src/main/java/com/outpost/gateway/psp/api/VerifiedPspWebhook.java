package com.outpost.gateway.psp.api;

import com.outpost.integration.psp.simulator.repository.PspConfiguration;

/** The PSP whose signature a PSP event notification matched. */
record VerifiedPspWebhook(PspConfiguration psp) {}
