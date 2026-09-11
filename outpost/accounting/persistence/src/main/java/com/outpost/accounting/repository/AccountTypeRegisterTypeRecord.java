package com.outpost.accounting.repository;

/** Database row for an account-type and register-type pair. */
public record AccountTypeRegisterTypeRecord(
    long accountTypeRegisterTypeId, long accountTypeId, long registerTypeId) {}
