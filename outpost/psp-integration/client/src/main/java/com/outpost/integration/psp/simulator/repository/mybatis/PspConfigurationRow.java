package com.outpost.integration.psp.simulator.repository.mybatis;

/** Persistence representation of PSP configuration. */
public record PspConfigurationRow(
    long accountId, String code, String baseUrl, String apiKey, String hmacSecret) {}
