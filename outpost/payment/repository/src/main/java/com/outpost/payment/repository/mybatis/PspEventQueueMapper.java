package com.outpost.payment.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.repository.PspEventRepository.PaymentAccounts;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** Maps PSP event queue operations. */
@RegisteredMapper
public interface PspEventQueueMapper {
  /** Finds the accounts and stored PSP reference for a payment reference. */
  @Nullable PaymentAccounts findPaymentAccounts(@Param("paymentReference") String paymentReference);

  /** Inserts a received PSP event. */
  int insertReceived(ReceivedPspEventRow event);

  /** Locks the oldest event that may be processed. */
  @Nullable PspEventRow claimCandidate();

  /** Marks a claimed event as being processed. */
  int markInProgress(@Param("queueId") long queueId);

  /** Records a terminal processing result. */
  int markDone(
      @Param("queueId") long queueId,
      @Param("resultId") long resultId,
      @Param("doneTs") Instant doneTs);
}
