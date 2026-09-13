package com.outpost.account.configuration.repository;

import com.outpost.account.Account;
import java.util.List;

/** Reads which PSPs are enabled for which merchants. */
public interface MerchantPspRepository {
  /** Returns whether the PSP account is enabled for the merchant account. */
  boolean isPspEnabled(long merchantAccountId, long pspAccountId);

  /** Finds the PSP accounts enabled for the merchant account, ordered by PSP code. */
  List<Account> findEnabledPsps(long merchantAccountId);
}
