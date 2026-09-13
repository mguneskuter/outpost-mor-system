package com.outpost.worker.psp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.accounting.queue.AccountingRequestTypes;
import com.outpost.accounting.queue.SubmitAccountingRequestCommand;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.repository.PspEventRepository;
import com.outpost.payment.repository.PspEventRepository.PspEvent;
import com.outpost.payment.repository.PspEventRepository.ReceivedPspEvent;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class PspEventProcessorTest {
  private final AccountingRequestQueue requests = mock(AccountingRequestQueue.class);
  private final TransactionTemplate transactions = mock(TransactionTemplate.class);

  @Test
  void createsCaptureRequestFromClaimedEvent() {
    PspEvent event =
        new PspEvent(
            41,
            21,
            "event-reference",
            "payment-reference",
            PspEventCodes.CAPTURE,
            """
            {"psp_reference":"psp-reference","success":true,"amount":1250,"currency":"EUR"}
            """);
    TestPspEventRepository events = new TestPspEventRepository(event);

    boolean processed = processor(events).processNext();

    ArgumentCaptor<SubmitAccountingRequestCommand> command =
        ArgumentCaptor.forClass(SubmitAccountingRequestCommand.class);
    verify(requests).submit(command.capture());
    assertThat(processed).isTrue();
    assertThat(command.getValue().getType()).isEqualTo(AccountingRequestTypes.CAPTURE_RESULT);
    assertThat(command.getValue().getReference()).isNotBlank();
    assertThat(command.getValue().getOriginalReference()).isEqualTo("payment-reference");
    assertThat(command.getValue().getAccountId()).isEqualTo(21);
    assertThat(command.getValue().getPspEventQueueId()).isEqualTo(41);
    assertThat(command.getValue().getSuccess()).isTrue();
    assertThat(command.getValue().getAmount()).isEqualTo(1250);
    assertThat(command.getValue().getCurrencyId()).isEqualTo(3);
    assertThat(command.getValue().getPspReference()).isEqualTo("psp-reference");
    assertThat(events.result).isEqualTo(PspEventResults.SUCCESS);
  }

  @Test
  void marksUnmappableEventFailedWithoutSubmittingRequest() {
    PspEvent event =
        new PspEvent(
            41,
            21,
            "event-reference",
            "payment-reference",
            PspEventCodes.CAPTURE,
            """
            {"psp_reference":"psp-reference","success":true,"amount":1250,"currency":"XXX"}
            """);
    TestPspEventRepository events = new TestPspEventRepository(event);

    boolean processed = processor(events).processNext();

    assertThat(processed).isTrue();
    assertThat(events.result).isEqualTo(PspEventResults.FAILED);
    verifyNoInteractions(requests);
  }

  @Test
  void marksRefundWithoutOurReferenceFailedWithoutSubmittingRequest() {
    PspEvent event =
        new PspEvent(
            41,
            21,
            "event-reference",
            "payment-reference",
            PspEventCodes.REFUND,
            """
            {"psp_reference":"psp-reference","success":true,"amount":1250,"currency":"EUR"}
            """);
    TestPspEventRepository events = new TestPspEventRepository(event);

    boolean processed = processor(events).processNext();

    assertThat(processed).isTrue();
    assertThat(events.result).isEqualTo(PspEventResults.FAILED);
    verifyNoInteractions(requests);
  }

  @Test
  void marksRefundWithBlankOurReferenceFailedWithoutSubmittingRequest() {
    PspEvent event =
        new PspEvent(
            41,
            21,
            "event-reference",
            "payment-reference",
            PspEventCodes.REFUND,
            """
            {"psp_reference":"psp-reference","success":true,"amount":1250,"currency":"EUR","refund_reference":" "}
            """);
    TestPspEventRepository events = new TestPspEventRepository(event);

    boolean processed = processor(events).processNext();

    assertThat(processed).isTrue();
    assertThat(events.result).isEqualTo(PspEventResults.FAILED);
    verifyNoInteractions(requests);
  }

  @ParameterizedTest
  @MethodSource("paymentReferenceEvents")
  void createsPaymentReferenceRequestForEvent(
      PspEventCodes eventCode, AccountingRequestTypes requestType) {
    TestPspEventRepository events =
        new TestPspEventRepository(
            new PspEvent(
                41,
                21,
                "event-reference",
                "payment-reference",
                eventCode,
                """
                {"psp_reference":"psp-reference","success":true,"amount":1250,"currency":"EUR"}
                """));

    processor(events).processNext();

    ArgumentCaptor<SubmitAccountingRequestCommand> command =
        ArgumentCaptor.forClass(SubmitAccountingRequestCommand.class);
    verify(requests).submit(command.capture());
    assertThat(command.getValue().getType()).isEqualTo(requestType);
    assertThat(command.getValue().getReference()).isEqualTo("payment-reference");
    assertThat(command.getValue().getAmount()).isNull();
    assertThat(command.getValue().getCurrencyId()).isNull();
    assertThat(events.result).isEqualTo(PspEventResults.SUCCESS);
  }

  @Test
  void createsRefundRequestUsingOurRefundReference() {
    TestPspEventRepository events =
        new TestPspEventRepository(
            new PspEvent(
                41,
                21,
                "event-reference",
                "payment-reference",
                PspEventCodes.REFUND,
                """
                {"psp_reference":"psp-reference","success":true,"amount":1250,"currency":"EUR","refund_reference":"refund-reference"}
                """));

    processor(events).processNext();

    ArgumentCaptor<SubmitAccountingRequestCommand> command =
        ArgumentCaptor.forClass(SubmitAccountingRequestCommand.class);
    verify(requests).submit(command.capture());
    assertThat(command.getValue().getType()).isEqualTo(AccountingRequestTypes.REFUND_RESULT);
    assertThat(command.getValue().getReference()).isEqualTo("refund-reference");
    assertThat(events.result).isEqualTo(PspEventResults.SUCCESS);
  }

  @Test
  @Timeout(10)
  void shutdownDoesNotAdmitClaimAfterStoppingBegins() throws Exception {
    PspEventProcessor processor = mock(PspEventProcessor.class);
    CountDownLatch admittedBeforeDatabaseClaim = new CountDownLatch(1);
    CountDownLatch allowDatabaseClaim = new CountDownLatch(1);
    CountDownLatch databaseClaimed = new CountDownLatch(1);
    CountDownLatch stopStarted = new CountDownLatch(1);
    PspEventRepository events = mock(PspEventRepository.class);
    when(events.claimNext())
        .thenAnswer(
            ignored -> {
              databaseClaimed.countDown();
              return Optional.empty();
            });
    when(processor.processNext(any()))
        .thenAnswer(
            ignored -> {
              admittedBeforeDatabaseClaim.countDown();
              allowDatabaseClaim.await();
              PspEventProcessor.PspEventClaimer claimer = ignored.getArgument(0);
              claimer.claimNext(events);
              return false;
            });
    PspEventPoller poller = new PspEventPoller(processor, 1, java.time.Duration.ofMinutes(1));
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      poller.start();
      assertThat(admittedBeforeDatabaseClaim.await(5, TimeUnit.SECONDS)).isTrue();

      // Submitted here so the stop begins concurrently with the spin-wait below; awaited only
      // after that wait and its assertions run, which checkstyle's declaration-distance check
      // cannot see is deliberate ordering rather than a stale variable.
      @SuppressWarnings("VariableDeclarationUsageDistance")
      Future<?> stopTask =
          executor.submit(
              () -> {
                stopStarted.countDown();
                poller.stop();
              });
      assertThat(stopStarted.await(5, TimeUnit.SECONDS)).isTrue();
      while (poller.isRunning()) {
        Thread.onSpinWait();
      }
      assertThat(poller.isRunning()).isFalse();
      allowDatabaseClaim.countDown();
      executor.shutdown();
      stopTask.get(5, TimeUnit.SECONDS);
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

      assertThat(poller.isRunning()).isFalse();
      assertThat(databaseClaimed.getCount()).isOne();
      verify(processor, times(1)).processNext(any());
    } finally {
      allowDatabaseClaim.countDown();
      executor.shutdownNow();
    }
  }

  private PspEventProcessor processor(PspEventRepository events) {
    when(transactions.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(mock(TransactionStatus.class));
            });
    return new PspEventProcessor(events, requests, new ObjectMapper(), transactions);
  }

  private static Stream<Arguments> paymentReferenceEvents() {
    return Stream.of(
        Arguments.of(PspEventCodes.AUTHORISATION, AccountingRequestTypes.AUTHORISATION_RESULT),
        Arguments.of(PspEventCodes.CANCELLATION, AccountingRequestTypes.CANCELLATION_RESULT));
  }

  private static final class TestPspEventRepository implements PspEventRepository {
    private final PspEvent event;
    private @Nullable PspEventResults result;

    TestPspEventRepository(PspEvent event) {
      this.event = event;
    }

    @Override
    public Optional<PaymentAccounts> findPaymentAccounts(String paymentReference) {
      return Optional.empty();
    }

    @Override
    public void recordReceived(ReceivedPspEvent receivedEvent) {}

    @Override
    public Optional<PspEvent> claimNext() {
      return Optional.of(event);
    }

    @Override
    public void complete(long queueId, PspEventResults completedResult) {
      result = completedResult;
    }
  }
}
