package com.outpost.accounting.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The calendar days a balance report covers, both inclusive and read in UTC, at most thirty days
 * long.
 */
public record ReportPeriod(LocalDate from, LocalDate to) {
  private static final int MAX_DAYS = 30;

  /**
   * Creates a period.
   *
   * @throws InvalidReportPeriodException when a date is absent, {@code to} is before {@code from},
   *     or the period is longer than thirty days
   */
  public ReportPeriod {
    if (from == null || to == null) {
      throw new InvalidReportPeriodException("A report period needs both dates");
    }
    if (to.isBefore(from)) {
      throw new InvalidReportPeriodException("A report period cannot end before it starts");
    }
    if (to.isAfter(from.plusDays(MAX_DAYS - 1))) {
      throw new InvalidReportPeriodException(
          "A report period covers at most " + MAX_DAYS + " days");
    }
  }

  /** The first instant in the period: {@code from} at midnight UTC. */
  public Instant postedFrom() {
    return from.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  /** The first instant after the period: the day after {@code to} at midnight UTC. */
  public Instant postedBefore() {
    return to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
  }
}
