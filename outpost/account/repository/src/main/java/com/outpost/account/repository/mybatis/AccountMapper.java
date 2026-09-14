package com.outpost.account.repository.mybatis;

import java.util.List;
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

  /** Finds the TAX_AUTHORITY account that collects tax for the country with this id. */
  @Nullable Account findTaxAuthorityAccountByCountryId(@Param("countryId") long countryId);

  /** Finds the accounts of the account type with this code. */
  List<Account> findAccountsByAccountType(@Param("accountType") String accountType);
}
