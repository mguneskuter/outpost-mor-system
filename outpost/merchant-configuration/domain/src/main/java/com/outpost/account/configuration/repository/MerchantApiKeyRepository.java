package com.outpost.account.configuration.repository;

import com.outpost.account.configuration.MerchantApiKey;
import java.util.Optional;

/** Reads merchant API keys. */
public interface MerchantApiKeyRepository {

  /** Finds the active API key whose SHA-256 hash, in lower-case hex, is {@code apiKeyHash}. */
  Optional<MerchantApiKey> findActiveMerchantApiKeyByApiKeyHash(String apiKeyHash);
}
