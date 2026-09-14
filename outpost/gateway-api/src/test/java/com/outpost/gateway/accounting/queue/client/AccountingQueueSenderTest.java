package com.outpost.gateway.accounting.queue.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.accounting.api.AccountingQueueApi;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.accounting.api.AccountingQueueResult;
import com.outpost.framework.queue.QueueItemResults;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class AccountingQueueSenderTest {
  private static final AccountingQueueRequest REQUEST =
      new AccountingQueueRequest(
          AccountingQueueRequestTypes.CAPTURE,
          "order-1",
          "merchant-order-1",
          "DEMO_PSP",
          "41",
          true,
          null,
          null,
          null,
          null,
          null,
          null,
          null);

  @ParameterizedTest(name = "{0}")
  @MethodSource("ledgerAnswers")
  void mapsEachLedgerAnswer(
      String scenario, @Nullable RuntimeException failure, QueueItemResults expected) {
    AccountingQueueSender sender = new AccountingQueueSender(new AnsweringLedger(failure));

    assertThat(sender.process(REQUEST)).isEqualTo(expected);
  }

  static Stream<Arguments> ledgerAnswers() {
    return Stream.of(
        Arguments.of("202 accepted", null, QueueItemResults.DONE),
        Arguments.of(
            "409 transaction locked",
            new HttpClientErrorException(HttpStatus.CONFLICT),
            QueueItemResults.RETRY_LATER),
        Arguments.of(
            "400 invalid request",
            new HttpClientErrorException(HttpStatus.BAD_REQUEST),
            QueueItemResults.DONE),
        Arguments.of(
            "503 service unavailable",
            new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE),
            QueueItemResults.RETRY_LATER),
        Arguments.of(
            "connection refused",
            new ResourceAccessException("refused"),
            QueueItemResults.RETRY_LATER));
  }

  /** A Ledger that answers 202, or throws the failure the test chose. */
  private static final class AnsweringLedger implements AccountingQueueApi {
    private final @Nullable RuntimeException failure;

    private AnsweringLedger(@Nullable RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public ResponseEntity<AccountingQueueResult> submit(AccountingQueueRequest request) {
      if (failure != null) {
        throw failure;
      }
      return ResponseEntity.accepted().body(AccountingQueueResult.accepted(request));
    }
  }
}
