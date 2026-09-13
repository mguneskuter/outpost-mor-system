package com.outpost.ledger.report.repository;

import java.time.Instant;
import java.util.List;

/**
 * Reads register balances for the balance reports. Every register of every account in scope is
 * returned, ordered by account code, register type code, and currency code; a line counts when its
 * entry was posted at or after {@code postedFrom} and before {@code postedBefore}.
 */
public interface BalanceReportRepository {
  /** Reads the registers of every merchant, tax authority, and platform account. */
  List<RegisterBalance> findPlatformRegisterBalances(Instant postedFrom, Instant postedBefore);

  /** Reads the registers of the merchant account with this code. */
  List<RegisterBalance> findMerchantRegisterBalances(
      String merchantCode, Instant postedFrom, Instant postedBefore);
}
