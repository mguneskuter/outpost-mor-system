package com.outpost.integration.psp.simulator.repository.mybatis;

import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;

/** Reads PSP configuration from {@code psp_configuration}. */
public final class MyBatisPspConfigurationRepository implements PspConfigurationRepository {
  private final PspConfigurationMapper mapper;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;

  /**
   * Creates a repository over a Spring-managed {@code sqlSession} whose returned configurations
   * carry these transport timeouts.
   */
  public MyBatisPspConfigurationRepository(
      SqlSession sqlSession, int connectTimeoutMillis, int readTimeoutMillis) {
    this.mapper = sqlSession.getMapper(PspConfigurationMapper.class);
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }

  @Override
  public Optional<com.outpost.integration.psp.simulator.PspConfiguration>
      findPspConfigurationByPspCode(String pspCode) {
    return Optional.ofNullable(mapper.findPspConfigurationByPspCode(pspCode))
        .map(this::toConfiguration);
  }

  private com.outpost.integration.psp.simulator.PspConfiguration toConfiguration(
      PspConfiguration row) {
    return new com.outpost.integration.psp.simulator.PspConfiguration(
        row.accountId(),
        row.code(),
        row.baseUrl(),
        row.apiKey(),
        row.hmacSecret(),
        connectTimeoutMillis,
        readTimeoutMillis);
  }
}
