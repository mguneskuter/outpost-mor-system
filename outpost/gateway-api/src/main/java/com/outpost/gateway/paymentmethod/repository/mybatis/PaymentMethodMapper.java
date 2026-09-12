package com.outpost.gateway.paymentmethod.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** MyBatis statements for a merchant's enabled payment methods. */
@RegisteredMapper
public interface PaymentMethodMapper {
  /** Finds the PSPs enabled for a merchant. */
  List<PaymentMethodRow> findEnabled(@Param("merchantAccountId") long merchantAccountId);
}
