package com.outpost.payment.refund.repository;

import com.outpost.payment.refund.Refund;

/** Stores merchant refunds. */
public interface RefundRepository {
  /** Stores an unsaved refund and returns it as stored, with its id and creation time. */
  Refund insertRefund(Refund refund);
}
