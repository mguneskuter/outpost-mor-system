package com.outpost.integration.psp.simulator.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** Maps PSP configuration rows. */
@RegisteredMapper
public interface PspConfigurationRepositoryMapper {
  /** Returns the configuration for a PSP account code. */
  @Nullable PspConfigurationData findByCode(@Param("code") String code);
}
