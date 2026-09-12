package com.outpost.pspsimulator;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** A create-order response: the PSP reference and the URL the card number is posted to. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateOrderResponse(String pspReference, String paymentUrl) {}
