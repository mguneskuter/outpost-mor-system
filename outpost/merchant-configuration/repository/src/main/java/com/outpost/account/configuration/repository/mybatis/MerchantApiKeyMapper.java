package com.outpost.account.configuration.repository.mybatis;

import com.outpost.account.configuration.MerchantApiKey;
import com.outpost.framework.persistence.RegisteredMapper;
import org.jspecify.annotations.Nullable;

/** MyBatis statements for merchant API keys. */
@RegisteredMapper
public interface MerchantApiKeyMapper {

  /** Finds the active key with this hash; {@code null} when there is none. */
  @Nullable MerchantApiKey findActiveMerchantApiKeyByApiKeyHash(String apiKeyHash);
}
