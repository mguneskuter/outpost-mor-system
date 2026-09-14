package com.outpost.payment.refund.repository;

import com.outpost.payment.refund.Refund;
import java.util.List;
import java.util.Optional;

/** Stores merchant refunds and the order lines each one claims. */
public interface RefundRepository {
  /**
   * Stores an unsaved refund and its items as one unit and returns them with their ids and the
   * refund's creation time.
   *
   * @return empty, with nothing stored, when any item's order line is already claimed by a refund
   *     that has not failed
   */
  Optional<Refund> insertRefund(Refund refund);

  /** Finds the refund with this reference, with its items. */
  Optional<Refund> findRefundByRefundReference(String refundReference);

  /** Finds every refund of the order with this reference, each with its items. */
  List<Refund> findRefundsByOriginalReference(String originalReference);

  /** Stores the PSP's reference for the refund with this reference. */
  void updateRefundPspRefundReference(String refundReference, String pspRefundReference);

  /** Marks every item of the refund with this reference as failed, which releases its lines. */
  void updateRefundItemRefundFailed(String refundReference);
}
