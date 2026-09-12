package com.outpost.worker.accounting.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** MyBatis mapper for the Worker's narrow read of Ledger's transaction tables. */
@RegisteredMapper
public interface LedgerTransactionMapper {
  /** Returns whether a CAPTURED event has ever been recorded for this payment's transaction. */
  boolean isCaptured(String paymentReference);

  /** Sums net and tax amounts refunded, or reserved, for one order line. */
  @Nullable RefundedTotalRow activeRefundedTotal(
      @Param("orderItemId") long orderItemId,
      @Param("excludingRefundReference") String excludingRefundReference);

  /** Finds a transaction's identity by its exact reference. */
  @Nullable Long findTransactionId(String reference);
}
