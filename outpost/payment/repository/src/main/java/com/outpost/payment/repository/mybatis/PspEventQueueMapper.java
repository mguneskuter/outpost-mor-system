package com.outpost.payment.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** Maps PSP event queue operations. */
@RegisteredMapper
public interface PspEventQueueMapper {
  /** Finds the accounts for a payment reference. */
  @Nullable PaymentAccountsRow findPaymentAccounts(
      @Param("paymentReference") String paymentReference);

  /** Inserts a received PSP event. */
  int insertReceived(ReceivedPspEventRow event);
}
