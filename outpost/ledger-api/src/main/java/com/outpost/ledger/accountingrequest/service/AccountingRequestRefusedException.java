package com.outpost.ledger.accountingrequest.service;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingRequestErrorTypes;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The Ledger refused an accounting request: nothing was queued and no lock is kept for it. */
public final class AccountingRequestRefusedException extends RuntimeException {
  private final AccountingRequestErrorTypes error;
  private final transient @Nullable AccountingQueueRequest request;

  /** Creates a refusal of {@code request}, absent when the body named none, for {@code error}. */
  public AccountingRequestRefusedException(
      AccountingRequestErrorTypes error, @Nullable AccountingQueueRequest request) {
    this.error = error;
    this.request = request;
  }

  /** Returns why the request was refused. */
  public AccountingRequestErrorTypes getError() {
    return error;
  }

  /** Returns the refused request; empty when the body named none. */
  public Optional<AccountingQueueRequest> getRequest() {
    return Optional.ofNullable(request);
  }
}
