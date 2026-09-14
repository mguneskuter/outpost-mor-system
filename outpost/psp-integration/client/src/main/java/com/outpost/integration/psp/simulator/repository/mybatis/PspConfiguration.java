package com.outpost.integration.psp.simulator.repository.mybatis;

record PspConfiguration(
    long accountId, String code, String baseUrl, String apiKey, String hmacSecret) {}
