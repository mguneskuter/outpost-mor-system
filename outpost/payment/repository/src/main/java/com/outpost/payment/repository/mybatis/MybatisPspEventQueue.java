package com.outpost.payment.repository.mybatis;

import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.PspEventStatuses;
import com.outpost.payment.repository.PspEventQueue;
import java.time.Instant;
import java.util.Arrays;
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

  @Override
  public Optional<PspEvent> claimNext() {
    PspEventRow candidate = mapper.claimCandidate();
    if (candidate == null) {
      return Optional.empty();
    }
    if (mapper.markInProgress(candidate.queueId()) != 1) {
      throw new IllegalStateException(
          "Could not mark PSP event in progress: " + candidate.queueId());
    }
    return Optional.of(
        new PspEvent(
            candidate.queueId(),
            candidate.merchantAccountId(),
            candidate.reference(),
            candidate.originalReference(),
            eventCode(candidate.eventCodeId()),
            candidate.payload()));
  }

  @Override
  public void complete(long queueId, PspEventResults result, Instant completedAt) {
    if (mapper.markDone(queueId, result.getValue().getPspEventResultId(), completedAt) != 1) {
      throw new IllegalStateException("PSP event is not in progress: " + queueId);
    }
  }

  private static PspEventCodes eventCode(long eventCodeId) {
    return Arrays.stream(PspEventCodes.values())
        .filter(value -> value.getValue().getPspEventCodeId() == eventCodeId)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown PSP event code: " + eventCodeId));
  }
}
