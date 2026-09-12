package com.outpost.gateway.report.service;

/** Controlled failure returned by the balance report boundary. */
public final class BalanceReportException extends RuntimeException {
  private final int status;
  private final String code;

  /** Creates a controlled balance report failure. */
  public BalanceReportException(int status, String code) {
    super(code);
    this.status = status;
    this.code = code;
  }

  /** Returns the HTTP status. */
  public int status() {
    return status;
  }

  /** Returns the stable error code. */
  public String code() {
    return code;
  }
}
