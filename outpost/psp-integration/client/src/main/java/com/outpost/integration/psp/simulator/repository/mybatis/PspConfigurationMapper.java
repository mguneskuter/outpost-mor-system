package com.outpost.integration.psp.simulator.repository.mybatis;

import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis statements for PSP configuration. Package-private, like the stored form it returns: a
 * MyBatis proxy of a public interface is defined in another module and cannot reach it.
 */
interface PspConfigurationMapper {
  @Nullable PspConfiguration findPspConfigurationByPspCode(@Param("pspCode") String pspCode);
}
