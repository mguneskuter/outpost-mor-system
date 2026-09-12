package com.outpost.payment.repository;

import com.outpost.payment.RefundItem;

/** Persistence boundary for the refunded portion of order lines. */
public interface RefundItemRepository {
  /** Records a refund item, or does nothing when this exact row already exists. */
  void insert(RefundItem item);
}
