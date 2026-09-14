package com.outpost.accounting.journalentry.repository.mybatis;

/** The registers of a CAPTURE entry's lines, with the accounts that hold them. */
record CaptureRegisters(
    long pspReceivableRegisterId,
    long pspAccountId,
    long taxPayableRegisterId,
    long taxAuthorityAccountId,
    long merchantPayableRegisterId,
    long merchantAccountId,
    long feeRevenueRegisterId,
    long platformAccountId) {}
