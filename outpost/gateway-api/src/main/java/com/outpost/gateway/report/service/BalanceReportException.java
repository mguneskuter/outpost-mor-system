package com.outpost.gateway.report.service;

/** A balance report request that is refused. */
public final class BalanceReportException extends RuntimeException {
  private final BalanceReportErrorCodes code;

  /** Creates the refusal for {@code code}. */
  public BalanceReportException(BalanceReportErrorCodes code) {
    super(code.name());
    this.code = code;
  }

  /** Returns why the request was refused. */
  public BalanceReportErrorCodes code() {
    return code;
  }
}
