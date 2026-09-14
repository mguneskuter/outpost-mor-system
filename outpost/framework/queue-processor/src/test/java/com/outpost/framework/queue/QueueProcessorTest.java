package com.outpost.framework.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QueueProcessorTest {
  private static final Duration ONE_MILLISECOND = Duration.ofMillis(1);

  private final TimeOrderedQueue<String> queue = new TimeOrderedQueue<>(Clock.systemUTC(), 10);
  private final List<String> handled = new CopyOnWriteArrayList<>();
  private @Nullable QueueProcessor<String> processor;

  @AfterEach
  void stopProcessor() {
    if (processor != null) {
      processor.stop();
    }
  }

  @Test
  void handsEveryDueItemToTheHandler() throws InterruptedException {
    queue.add("A");
    queue.add("B");
    queue.add("C");
    CountDownLatch handledAll = new CountDownLatch(3);

    start(
        payload -> {
          handled.add(payload);
          handledAll.countDown();
          return QueueItemResults.DONE;
        },
        2,
        10);

    assertThat(handledAll.await(5, TimeUnit.SECONDS)).isTrue();
    stop();
    assertThat(handled).containsExactlyInAnyOrder("A", "B", "C");
    assertThat(queue.size()).isZero();
  }

  @Test
  void triesAnItemAgainAfterRetryLater() throws InterruptedException {
    CountDownLatch handledTwice = new CountDownLatch(2);
    AtomicInteger calls = new AtomicInteger();
    queue.add("A");

    start(
        payload -> {
          handled.add(payload);
          handledTwice.countDown();
          return calls.incrementAndGet() == 1
              ? QueueItemResults.RETRY_LATER
              : QueueItemResults.DONE;
        },
        1,
        10);

    assertThat(handledTwice.await(5, TimeUnit.SECONDS)).isTrue();
    stop();
    assertThat(handled).containsExactly("A", "A");
    assertThat(queue.size()).isZero();
  }

  @Test
  void dropsAnItemAfterMaxAttempts() throws InterruptedException {
    CountDownLatch handledThreeTimes = new CountDownLatch(3);
    queue.add("A");

    start(
        payload -> {
          handled.add(payload);
          handledThreeTimes.countDown();
          return QueueItemResults.RETRY_LATER;
        },
        1,
        3);

    assertThat(handledThreeTimes.await(5, TimeUnit.SECONDS)).isTrue();
    stop();
    assertThat(handled).hasSize(3);
    assertThat(queue.size()).isZero();
  }

  @Test
  void treatsHandlerExceptionAsRetryLater() throws InterruptedException {
    CountDownLatch handledTwice = new CountDownLatch(2);
    AtomicInteger calls = new AtomicInteger();
    queue.add("A");

    start(
        payload -> {
          handled.add(payload);
          handledTwice.countDown();
          if (calls.incrementAndGet() == 1) {
            throw new IllegalStateException("first attempt fails");
          }
          return QueueItemResults.DONE;
        },
        1,
        10);

    assertThat(handledTwice.await(5, TimeUnit.SECONDS)).isTrue();
    stop();
    assertThat(handled).containsExactly("A", "A");
    assertThat(queue.size()).isZero();
  }

  private void start(QueueItemProcessor<String> handler, int workerCount, int maxAttempts) {
    processor =
        new QueueProcessor<>(
            "test-queue",
            queue,
            handler,
            new QueueProcessorSettings(workerCount, ONE_MILLISECOND, ONE_MILLISECOND, maxAttempts));
    processor.start();
  }

  private void stop() {
    if (processor != null) {
      processor.stop();
      processor = null;
    }
  }
}
