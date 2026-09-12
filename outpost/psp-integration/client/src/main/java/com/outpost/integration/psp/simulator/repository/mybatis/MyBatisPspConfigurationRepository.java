package com.outpost.integration.psp.simulator.repository.mybatis;

import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import java.util.Objects;
import java.util.Optional;

/** MyBatis implementation of the PSP configuration repository. */
public final class MyBatisPspConfigurationRepository implements PspConfigurationRepository {
  private final PspConfigurationMapper mapper;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;

  /** Creates a repository with transport timeouts applied to returned configurations. */
  public MyBatisPspConfigurationRepository(
      PspConfigurationMapper mapper, int connectTimeoutMillis, int readTimeoutMillis) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }

  @Override
  public Optional<PspConfiguration> findByCode(String code) {
    return Optional.ofNullable(mapper.findByCode(code)).map(this::toConfiguration);
  }

  private PspConfiguration toConfiguration(PspConfigurationRow data) {
    return new PspConfiguration(
        data.accountId(),
        data.code(),
        data.baseUrl(),
        data.apiKey(),
        data.hmacSecret(),
        connectTimeoutMillis,
        readTimeoutMillis);
  }
}
