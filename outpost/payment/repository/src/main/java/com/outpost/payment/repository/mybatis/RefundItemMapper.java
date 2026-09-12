package com.outpost.payment.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for {@code refund_item}. */
@RegisteredMapper
public interface RefundItemMapper {
  /** Inserts a refund item, doing nothing when this exact row already exists. */
  void insert(
      @Param("refundId") long refundId,
      @Param("orderItemId") long orderItemId,
      @Param("netAmount") long netAmount,
      @Param("taxAmount") long taxAmount);
}
