package com.outpost.account.configuration;

/** A merchant's active API key: the account it authenticates and its encrypted HMAC secret. */
public record MerchantApiKey(long accountId, String encryptedHmacSecret) {}
