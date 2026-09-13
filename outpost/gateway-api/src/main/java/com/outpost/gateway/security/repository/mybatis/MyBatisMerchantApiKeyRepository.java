package com.outpost.gateway.security.repository.mybatis;

import com.outpost.gateway.security.repository.MerchantApiKeyCredentials;
import com.outpost.gateway.security.repository.MerchantApiKeyRepository;
import java.util.Objects;
import java.util.Optional;

/** MyBatis implementation of the merchant API-key repository. */
public final class MyBatisMerchantApiKeyRepository implements MerchantApiKeyRepository {

  private final MerchantApiKeyMapper mapper;

  /** Creates a repository backed by the merchant API-key mapper. */
  public MyBatisMerchantApiKeyRepository(MerchantApiKeyMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "merchant API-key mapper");
  }

  @Override
  public Optional<MerchantApiKeyCredentials> findActiveByHash(String apiKeyHashHex) {
    return Optional.ofNullable(mapper.findActiveByHash(apiKeyHashHex));
  }
}
