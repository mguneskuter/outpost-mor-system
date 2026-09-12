package com.outpost.payment.repository.mybatis;

import com.outpost.payment.RefundItem;
import com.outpost.payment.repository.RefundItemRepository;
import java.util.Objects;

/** MyBatis implementation of {@link RefundItemRepository}. */
public final class MyBatisRefundItemRepository implements RefundItemRepository {
  private final RefundItemMapper mapper;

  /** Creates a repository over the generated mapper. */
  public MyBatisRefundItemRepository(RefundItemMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public void insert(RefundItem item) {
    mapper.insert(
        item.getRefundId(),
        item.getOrderItemId(),
        item.getNetAmount().quantity(),
        item.getTaxAmount().quantity());
  }
}
