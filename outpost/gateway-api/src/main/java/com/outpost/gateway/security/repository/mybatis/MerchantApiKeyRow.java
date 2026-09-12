package com.outpost.gateway.security.repository.mybatis;

record MerchantApiKeyRow(long accountId, String encryptedHmacSecret) {}
