package com.outpost.worker.accounting.repository.mybatis;

import com.outpost.worker.accounting.repository.LedgerTransactionRepository;
import java.util.Objects;
import java.util.Optional;

/** MyBatis implementation of {@link LedgerTransactionRepository}. */
public final class MyBatisLedgerTransactionRepository implements LedgerTransactionRepository {
  private final LedgerTransactionMapper mapper;

  /** Creates a repository over the generated mapper. */
  public MyBatisLedgerTransactionRepository(LedgerTransactionMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public boolean isCaptured(String paymentReference) {
    return mapper.isCaptured(paymentReference);
  }

  @Override
  public RefundedTotal activeRefundedTotal(long orderItemId, String excludingRefundReference) {
    RefundedTotalRow row = mapper.activeRefundedTotal(orderItemId, excludingRefundReference);
    return row == null ? new RefundedTotal(0, 0) : new RefundedTotal(row.net(), row.tax());
  }

  @Override
  public Optional<Long> findTransactionId(String reference) {
    return Optional.ofNullable(mapper.findTransactionId(reference));
  }
}
