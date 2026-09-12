package com.outpost.accounting.queue;

import com.outpost.accounting.queue.repository.mybatis.AccountingRequestLineRow;
import com.outpost.accounting.queue.repository.mybatis.AccountingRequestQueueMapper;
import com.outpost.accounting.queue.repository.mybatis.AccountingRequestRow;
import com.outpost.accounting.queue.repository.mybatis.NewAccountingRequestRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** Transactional boundary for submitting and processing accounting requests. */
public class AccountingRequestQueue {
  private final AccountingRequestQueueMapper mapper;
  private final Clock clock;
  private final Duration leaseDuration;

  /** Creates a queue using a clock and the configured payment-lock lease. */
  public AccountingRequestQueue(
      AccountingRequestQueueMapper mapper, Clock clock, Duration leaseDuration) {
    this.mapper = mapper;
    this.clock = clock;
    if (leaseDuration.isZero() || leaseDuration.isNegative()) {
      throw new IllegalArgumentException("leaseDuration must be positive");
    }
    this.leaseDuration = leaseDuration;
  }

  /** Submits work, returning the existing request when either idempotency key is duplicated. */
  @Transactional
  public AccountingRequest submit(SubmitAccountingRequestCommand command) {
    Instant createdAt = now();
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
            createdAt,
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
    Instant lockedAt = now();
    AccountingRequestRow candidate = mapper.claimCandidate(lockedAt);
    if (candidate == null) {
      return Optional.empty();
    }
    if (candidate.transactionId() == null) {
      mapper.markMissingPaymentFailed(candidate.queueId(), lockedAt);
      return Optional.empty();
    }
    if (mapper.markInProgress(candidate.queueId()) != 1) {
      throw new IllegalStateException("Could not mark request in progress: " + candidate.queueId());
    }
    Instant leaseUntil = lockedAt.plus(leaseDuration);
    if (mapper.takePaymentLock(candidate.transactionId(), candidate.queueId(), lockedAt, leaseUntil)
        != 1) {
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
    if (mapper.markDone(queueId, result.getValue().accountingRequestResultId(), now()) != 1) {
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

  private Instant now() {
    return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
  }
}
