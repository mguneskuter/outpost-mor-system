package com.outpost.pspsimulator;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** A refund response: the PSP's refund reference and whether the refund was accepted. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RefundResponse(String pspRefundReference, boolean accepted) {}
