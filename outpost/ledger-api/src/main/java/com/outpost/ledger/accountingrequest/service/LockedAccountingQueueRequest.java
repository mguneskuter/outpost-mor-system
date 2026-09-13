package com.outpost.ledger.accountingrequest.service;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.transactionlock.TransactionLock;

/** An accepted accounting request and the transaction lock taken for it. */
public record LockedAccountingQueueRequest(
    AccountingQueueRequest request, TransactionLock transactionLock) {}
