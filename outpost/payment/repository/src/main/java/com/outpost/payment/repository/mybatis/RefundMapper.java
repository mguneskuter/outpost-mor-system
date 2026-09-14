package com.outpost.payment.repository.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis statements for merchant refunds and their items. Package-private, like the stored forms
 * it returns: a MyBatis proxy of a public interface is defined in another module and cannot reach
 * package-private result types.
 */
interface RefundMapper {
  /** Inserts a refund and returns it as stored. */
  Refund insertRefund(com.outpost.payment.refund.Refund refund);

  /**
   * Claims one order line for a refund and returns the item as stored, or null when a refund that
   * has not failed already claims the line.
   */
  @Nullable RefundItem insertRefundItem(
      @Param("refundId") long refundId, @Param("orderItemId") long orderItemId);

  /** Finds a refund by its reference. */
  @Nullable Refund findRefundByRefundReference(@Param("refundReference") String refundReference);

  /** Finds the refunds of an order in the order they were inserted. */
  List<Refund> findRefundsByOriginalReference(@Param("originalReference") String originalReference);

  /** Finds a refund's items in the order they were inserted. */
  List<RefundItem> findRefundItems(@Param("refundId") long refundId);

  /** Finds the items of every refund of an order, by refund and then in insertion order. */
  List<RefundItem> findRefundItemsByOriginalReference(
      @Param("originalReference") String originalReference);

  /** Stores the PSP's reference on a refund. */
  int updateRefundPspRefundReference(
      @Param("refundReference") String refundReference,
      @Param("pspRefundReference") String pspRefundReference);

  /** Marks every item of a refund as failed. */
  int updateRefundItemRefundFailed(@Param("refundReference") String refundReference);
}
