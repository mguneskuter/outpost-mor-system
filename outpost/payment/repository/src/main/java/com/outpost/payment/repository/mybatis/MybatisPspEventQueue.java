package com.outpost.payment.repository.mybatis;

import com.outpost.payment.PspEventStatuses;
import com.outpost.payment.repository.PspEventQueue;
import java.util.Optional;

/** MyBatis-backed PSP event queue. */
public final class MybatisPspEventQueue implements PspEventQueue {
  private final PspEventQueueMapper mapper;

  /** Creates a queue backed by the supplied mapper. */
  public MybatisPspEventQueue(PspEventQueueMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<PaymentAccounts> findPaymentAccounts(String paymentReference) {
    return Optional.ofNullable(mapper.findPaymentAccounts(paymentReference))
        .map(row -> new PaymentAccounts(row.merchantAccountId(), row.pspAccountId()));
  }

  @Override
  public void recordReceived(ReceivedPspEvent event) {
    mapper.insertReceived(
        new ReceivedPspEventRow(
            event.merchantAccountId(),
            event.pspAccountId(),
            PspEventStatuses.RECEIVED.getValue().getPspEventStatusId(),
            event.reference(),
            event.originalReference(),
            event.eventCode().getValue().getPspEventCodeId(),
            event.payload()));
  }
}
