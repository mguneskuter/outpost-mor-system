package com.outpost.gateway.report.service;

/** Why a balance report request is refused; each name is the error code the API answers. */
public enum BalanceReportErrorCodes {
  INVALID_REPORT_PERIOD,
  MERCHANT_NOT_FOUND,
  REPORT_NOT_FOUND
}
