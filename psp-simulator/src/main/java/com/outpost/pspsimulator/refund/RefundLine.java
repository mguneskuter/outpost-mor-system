package com.outpost.pspsimulator.refund;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * One refunded order line as the caller sends it and the REFUND webhook echoes it; the simulator
 * does not act on it.
 *
 * @param taxRate the rate as the caller wrote it
 * @param netAmount the line's net in minor units
 * @param grossAmount the line's net plus tax in minor units
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RefundLine(
    String orderLineReference, String taxRate, long netAmount, long grossAmount) {}
