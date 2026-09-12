package com.outpost.gateway.report.repository;

import java.util.Optional;

/** Persistence boundary for balance report scoping. */
public interface ReportRepository {
  /** Reads the account code of an active merchant. */
  Optional<String> findMerchantCode(long accountId);
}
