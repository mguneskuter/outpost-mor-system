package com.outpost.account.configuration.repository.mybatis;

import com.outpost.account.configuration.MerchantApiKey;
import com.outpost.account.configuration.repository.MerchantApiKeyRepository;
import java.util.Objects;
import java.util.Optional;

/** Reads merchant API keys from {@code merchant_api_key}. */
public final class MyBatisMerchantApiKeyRepository implements MerchantApiKeyRepository {

  private final MerchantApiKeyMapper mapper;

  /** Creates a repository over the mapper. */
  public MyBatisMerchantApiKeyRepository(MerchantApiKeyMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "merchant API-key mapper");
  }

  @Override
  public Optional<MerchantApiKey> findActiveMerchantApiKeyByApiKeyHash(String apiKeyHash) {
    return Optional.ofNullable(mapper.findActiveMerchantApiKeyByApiKeyHash(apiKeyHash));
  }
}
