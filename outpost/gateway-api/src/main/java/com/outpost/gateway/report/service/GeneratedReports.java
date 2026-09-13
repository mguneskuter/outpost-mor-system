package com.outpost.gateway.report.service;

import com.outpost.accounting.report.BalanceReport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The reports the Gateway has built, held in memory under the identifier each report's URL carries.
 * At capacity, adding a report evicts the oldest added one. Thread-safe.
 */
public final class GeneratedReports {
  private final int capacity;
  private final Map<UUID, BalanceReport> reports;

  /** Creates a store that keeps at most {@code capacity} reports. */
  public GeneratedReports(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("Capacity must be positive: " + capacity);
    }
    this.capacity = capacity;
    this.reports =
        new LinkedHashMap<>() {
          @Override
          protected boolean removeEldestEntry(Map.Entry<UUID, BalanceReport> eldest) {
            return size() > GeneratedReports.this.capacity;
          }
        };
  }

  /** Stores the report and returns the identifier it can be read under. */
  public UUID add(BalanceReport report) {
    UUID reportId = UUID.randomUUID();
    synchronized (reports) {
      reports.put(reportId, report);
    }
    return reportId;
  }

  /** Reads a stored report, or empty when none is stored under the identifier. */
  public Optional<BalanceReport> find(UUID reportId) {
    synchronized (reports) {
      return Optional.ofNullable(reports.get(reportId));
    }
  }
}
