package com.outpost.gateway.security.repository.mybatis;

import com.outpost.gateway.security.repository.MerchantApiKeyCredentials;
import com.outpost.gateway.security.repository.MerchantApiKeyRepository;
import java.util.Objects;
import java.util.Optional;

/** MyBatis implementation of the merchant API-key repository. */
public final class MybatisMerchantApiKeyRepository implements MerchantApiKeyRepository {

  private final MerchantApiKeyRepositoryMapper mapper;

  /** Creates a repository backed by the merchant API-key mapper. */
  public MybatisMerchantApiKeyRepository(MerchantApiKeyRepositoryMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "merchant API-key mapper");
  }

  @Override
  public Optional<MerchantApiKeyCredentials> findActiveByHash(String apiKeyHash) {
    MerchantApiKeyRow row = mapper.findActiveByHash(apiKeyHash);
    return Optional.ofNullable(row)
        .map(
            value -> new MerchantApiKeyCredentials(value.accountId(), value.encryptedHmacSecret()));
  }
}
