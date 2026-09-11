package com.outpost.integration.psp.service;

/** Request to cancel an uncaptured payment service provider order. */
public record CancelRequest(String pspCode, String pspReference) {}
