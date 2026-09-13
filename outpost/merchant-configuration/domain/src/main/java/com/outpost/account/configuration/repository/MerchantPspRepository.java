package com.outpost.account.configuration.repository;

/** Reads which PSPs are enabled for which merchants. */
public interface MerchantPspRepository {
  /** Returns whether the PSP account is enabled for the merchant account. */
  boolean isPspEnabled(long merchantAccountId, long pspAccountId);
}
