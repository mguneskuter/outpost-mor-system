package com.outpost.accounting.report;

/** The requested dates do not form a period a balance report can cover. */
public final class InvalidReportPeriodException extends RuntimeException {
  InvalidReportPeriodException(String message) {
    super(message);
  }
}
