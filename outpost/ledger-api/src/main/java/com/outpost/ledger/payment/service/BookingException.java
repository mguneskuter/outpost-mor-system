package com.outpost.ledger.payment.service;

import org.jspecify.annotations.Nullable;

/** An accounting request that is not booked; nothing it would have written is stored. */
public final class BookingException extends RuntimeException {
  private final BookingErrorCodes code;

  /** Creates the failure for {@code code}, keeping the exception that revealed it. */
  public BookingException(BookingErrorCodes code, @Nullable Throwable cause) {
    super(code.name(), cause);
    this.code = code;
  }

  /** Returns why the request was not booked. */
  public BookingErrorCodes code() {
    return code;
  }
}
