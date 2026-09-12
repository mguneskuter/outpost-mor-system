package com.outpost.worker.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.accounting.queue.AccountingRequestQueue;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AccountingRequestProcessorTest {
  @Test
  @Timeout(10)
  void shutdownDoesNotAdmitClaimAfterStoppingBegins() throws Exception {
    AccountingRequestProcessor processor = mock(AccountingRequestProcessor.class);
    CountDownLatch admittedBeforeDatabaseClaim = new CountDownLatch(1);
    CountDownLatch allowDatabaseClaim = new CountDownLatch(1);
    CountDownLatch databaseClaimed = new CountDownLatch(1);
    CountDownLatch stopStarted = new CountDownLatch(1);
    AccountingRequestQueue requests = mock(AccountingRequestQueue.class);
    when(requests.claimNext())
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
              AccountingRequestProcessor.AccountingRequestClaimer claimer = ignored.getArgument(0);
              claimer.claimNext(requests);
              return false;
            });
    AccountingRequestPoller poller =
        new AccountingRequestPoller(processor, 1, Duration.ofMinutes(1));
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      poller.start();
      assertThat(admittedBeforeDatabaseClaim.await(5, TimeUnit.SECONDS)).isTrue();

      var unused =
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
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

      assertThat(poller.isRunning()).isFalse();
      assertThat(databaseClaimed.getCount()).isOne();
      verify(processor, times(1)).processNext(any());
    } finally {
      allowDatabaseClaim.countDown();
      executor.shutdownNow();
    }
  }
}
