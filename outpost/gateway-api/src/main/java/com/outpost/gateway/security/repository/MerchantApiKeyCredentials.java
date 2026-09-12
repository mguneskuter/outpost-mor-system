package com.outpost.gateway.security.repository;

/** Credentials needed to authenticate a merchant request. */
public record MerchantApiKeyCredentials(long accountId, String encryptedHmacSecret) {}
