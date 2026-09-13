package com.outpost.framework.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeOrderedQueueTest {
  private final MutableClock clock = new MutableClock(Instant.parse("2026-09-13T10:00:00Z"));
  private final TimeOrderedQueue<String> queue = new TimeOrderedQueue<>(clock);

  @Test
  void returnsNothingBeforeAnItemIsDue() {
    queue.add("A");
    clock.advance(Duration.ofSeconds(-1));

    assertThat(queue.pollDue()).isEmpty();
    assertThat(queue.size()).isEqualTo(1);
  }

  @Test
  void returnsItemsDueTogetherInArrivalOrder() {
    queue.add("A");
    queue.add("B");

    assertThat(queue.pollDue()).map(QueuedItem::payload).contains("A");
    assertThat(queue.pollDue()).map(QueuedItem::payload).contains("B");
    assertThat(queue.pollDue()).isEmpty();
  }

  @Test
  void returnsAnItemAddedAgainAfterItsRetryDelay() {
    queue.add("A");
    QueuedItem<String> first = queue.pollDue().orElseThrow();

    queue.addAgain(first, Duration.ofSeconds(5));
    queue.add("B");

    assertThat(queue.pollDue()).map(QueuedItem::payload).contains("B");
    assertThat(queue.pollDue()).isEmpty();
    clock.advance(Duration.ofSeconds(5));
    QueuedItem<String> again = queue.pollDue().orElseThrow();
    assertThat(again.payload()).isEqualTo("A");
    assertThat(again.attempts()).isEqualTo(1);
    assertThat(again.sequence()).isEqualTo(first.sequence());
  }

  @Test
  void keepsTheOriginalSequenceOfAnItemAddedAgain() {
    queue.add("A");
    QueuedItem<String> first = queue.pollDue().orElseThrow();

    queue.addAgain(first, Duration.ZERO);
    queue.add("B");

    assertThat(queue.pollDue()).map(QueuedItem::payload).contains("A");
    assertThat(queue.pollDue()).map(QueuedItem::payload).contains("B");
  }
}
