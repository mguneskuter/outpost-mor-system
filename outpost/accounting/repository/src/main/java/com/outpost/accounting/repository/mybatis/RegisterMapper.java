package com.outpost.accounting.repository.mybatis;

import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** MyBatis statements for registers. */
interface RegisterMapper {
  @Nullable Long findRegisterIdByAccountAndRegisterType(
      @Param("accountId") long accountId, @Param("registerType") String registerType);
}
