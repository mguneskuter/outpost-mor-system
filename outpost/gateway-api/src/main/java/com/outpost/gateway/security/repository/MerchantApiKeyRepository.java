package com.outpost.gateway.security.repository;

import java.util.Optional;

/** Reads active merchant API-key credentials. */
public interface MerchantApiKeyRepository {

  /** Finds the active credentials for an API-key hash. */
  Optional<MerchantApiKeyCredentials> findActiveByHash(String apiKeyHash);
}
