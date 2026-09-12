package com.outpost.gateway.security.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;

/** Maps active merchant API-key rows. */
@RegisteredMapper
public interface MerchantApiKeyRepositoryMapper {

  /** Finds an active merchant API key by its SHA-256 hash. */
  MerchantApiKeyRow findActiveByHash(String apiKeyHash);
}
