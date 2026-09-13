package com.outpost.accounting.queue;

import com.outpost.accounting.queue.repository.mybatis.AccountingRequestLineRow;
import com.outpost.accounting.queue.repository.mybatis.AccountingRequestQueueMapper;
import com.outpost.accounting.queue.repository.mybatis.AccountingRequestRow;
import com.outpost.accounting.queue.repository.mybatis.NewAccountingRequestRow;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional boundary for submitting and processing accounting requests. Request and lock times
 * are the database transaction's time, so every deployable leases payments by one clock.
 */
public class AccountingRequestQueue {
  private final AccountingRequestQueueMapper mapper;
  private final long leaseMicros;

  /**
   * Creates a queue with the configured payment-lock lease.
   *
   * @throws IllegalArgumentException when the lease is not a positive whole number of microseconds,
   *     the precision of a stored lease end
   */
  public AccountingRequestQueue(AccountingRequestQueueMapper mapper, Duration leaseDuration) {
    this.mapper = mapper;
    Duration microsecond = ChronoUnit.MICROS.getDuration();
    this.leaseMicros = leaseDuration.dividedBy(microsecond);
    if (leaseMicros <= 0 || !microsecond.multipliedBy(leaseMicros).equals(leaseDuration)) {
      throw new IllegalArgumentException(
          "leaseDuration must be a positive whole number of microseconds");
    }
  }

  /** Submits work, returning the existing request when either idempotency key is duplicated. */
  @Transactional
  public AccountingRequest submit(SubmitAccountingRequestCommand command) {
    AccountingRequestRow existing =
        mapper.findExisting(
            command.getType().getValue().accountingRequestTypeId(),
            command.getReference(),
            command.getAccountId(),
            command.getIdempotencyKey(),
            command.getPspEventQueueId());
    if (existing != null) {
      return load(existing.queueId());
    }
    NewAccountingRequestRow row =
        new NewAccountingRequestRow(
            command.getType().getValue().accountingRequestTypeId(),
            command.getReference(),
            command.getOriginalReference(),
            command.getAccountId(),
            command.getPspEventQueueId(),
            command.getIdempotencyKey(),
            command.getMerchantReference(),
            command.getSuccess(),
            command.getAmount(),
            command.getCurrencyId(),
            command.getPspReference());
    Long queueId = mapper.insertRequest(row);
    if (queueId == null) {
      AccountingRequestRow concurrent =
          mapper.findExisting(
              command.getType().getValue().accountingRequestTypeId(),
              command.getReference(),
              command.getAccountId(),
              command.getIdempotencyKey(),
              command.getPspEventQueueId());
      if (concurrent == null) {
        throw new IllegalStateException("Request insert conflicted without an existing request");
      }
      return load(concurrent.queueId());
    }
    for (AccountingRequestLine line : command.getLines()) {
      mapper.insertLine(queueId, line.orderLineReference(), line.amount());
    }
    return load(queueId);
  }

  /** Claims the oldest unfinished request whose payment is not currently leased. */
  @Transactional
  public Optional<AccountingRequest> claimNext() {
    AccountingRequestRow candidate = mapper.claimCandidate();
    if (candidate == null) {
      return Optional.empty();
    }
    if (candidate.transactionId() == null) {
      mapper.markMissingPaymentFailed(candidate.queueId());
      return Optional.empty();
    }
    if (mapper.markInProgress(candidate.queueId()) != 1) {
      throw new IllegalStateException("Could not mark request in progress: " + candidate.queueId());
    }
    if (mapper.takePaymentLock(candidate.transactionId(), candidate.queueId(), leaseMicros) != 1) {
      throw new IllegalStateException(
          "Could not acquire payment lock: " + candidate.transactionId());
    }
    return Optional.of(load(candidate.queueId()));
  }

  /** Completes a claimed request and releases its payment lock. */
  @Transactional
  public void complete(AccountingRequest request, AccountingRequestResults result) {
    complete(request.getQueueId(), result);
  }

  /** Completes a claimed request and releases its payment lock. */
  @Transactional
  public void complete(long queueId, AccountingRequestResults result) {
    if (mapper.markDone(queueId, result.getValue().accountingRequestResultId()) != 1) {
      throw new IllegalStateException("Request is not an unfinished request: " + queueId);
    }
    mapper.deletePaymentLock(queueId);
  }

  private AccountingRequest load(long queueId) {
    AccountingRequestRow row = mapper.findById(queueId);
    if (row == null) {
      throw new IllegalStateException("Request disappeared: " + queueId);
    }
    List<AccountingRequestLine> lines =
        mapper.findLines(queueId).stream().map(AccountingRequestQueue::line).toList();
    return toRequest(row, lines);
  }

  private static AccountingRequestLine line(AccountingRequestLineRow row) {
    return new AccountingRequestLine(row.orderLineReference(), row.amount());
  }

  private static AccountingRequest toRequest(
      AccountingRequestRow row, List<AccountingRequestLine> lines) {
    return new AccountingRequest(
        row.queueId(),
        row.createdTs(),
        row.doneTs(),
        row.done(),
        byId(AccountingRequestStatuses.values(), row.statusId(), "status"),
        row.resultId() == null
            ? null
            : byId(AccountingRequestResults.values(), row.resultId(), "result"),
        byId(AccountingRequestTypes.values(), row.typeId(), "type"),
        row.reference(),
        row.originalReference(),
        row.accountId(),
        row.pspEventQueueId(),
        row.idempotencyKey(),
        row.merchantReference(),
        row.success(),
        row.amount(),
        row.currencyId(),
        row.pspReference(),
        row.transactionId(),
        lines);
  }

  private static <E extends Enum<E>> E byId(E[] values, long id, String name) {
    return Arrays.stream(values)
        .filter(value -> valueId(value) == id)
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Unknown accounting request " + name + ": " + id));
  }

  private static long valueId(Enum<?> value) {
    return switch (value) {
      case AccountingRequestTypes type -> type.getValue().accountingRequestTypeId();
      case AccountingRequestStatuses status -> status.getValue().accountingRequestStatusId();
      case AccountingRequestResults result -> result.getValue().accountingRequestResultId();
      default -> throw new IllegalArgumentException("Unsupported request enum: " + value);
    };
  }
}
