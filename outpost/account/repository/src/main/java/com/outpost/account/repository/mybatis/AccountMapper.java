package com.outpost.account.repository.mybatis;

import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis statements for accounts. Package-private, like the stored form it returns: a MyBatis
 * proxy of a public interface is defined in another module and cannot reach package-private result
 * types.
 */
interface AccountMapper {
  /** Finds the account with this id. */
  @Nullable Account findAccountById(@Param("accountId") long accountId);

  /** Finds the account with this code. */
  @Nullable Account findAccountByCode(@Param("code") String code);
}
