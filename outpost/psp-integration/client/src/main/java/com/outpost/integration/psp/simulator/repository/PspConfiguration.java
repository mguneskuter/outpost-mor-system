package com.outpost.integration.psp.simulator.repository;

/** PSP connection and credential settings. */
public record PspConfiguration(
    long accountId,
    String code,
    String baseUrl,
    String apiKey,
    String hmacSecret,
    int connectTimeoutMillis,
    int readTimeoutMillis) {}
