package com.outpost.framework.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.queue.testfixtures.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeOrderedQueueTest {
  private static final int CAPACITY = 2;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-09-13T10:00:00Z"));
  private final TimeOrderedQueue<String> queue = new TimeOrderedQueue<>(clock, CAPACITY);

  @Test
  void returnsNothingBeforeAnItemIsDue() {
    queue.add("A");
    clock.advance(Duration.ofSeconds(-1));

    assertThat(queue.poll()).isEmpty();
    assertThat(queue.size()).isEqualTo(1);
  }

  @Test
  void returnsItemsDueTogetherInArrivalOrder() {
    queue.add("A");
    queue.add("B");

    assertThat(queue.poll()).map(QueuedItem::payload).contains("A");
    assertThat(queue.poll()).map(QueuedItem::payload).contains("B");
    assertThat(queue.poll()).isEmpty();
  }

  @Test
  void returnsAnItemAddedAgainAfterItsRetryDelay() {
    queue.add("A");
    QueuedItem<String> first = queue.poll().orElseThrow();

    queue.add(first, Duration.ofSeconds(5));
    queue.add("B");

    assertThat(queue.poll()).map(QueuedItem::payload).contains("B");
    assertThat(queue.poll()).isEmpty();
    clock.advance(Duration.ofSeconds(5));
    QueuedItem<String> again = queue.poll().orElseThrow();
    assertThat(again.payload()).isEqualTo("A");
    assertThat(again.attempts()).isEqualTo(1);
    assertThat(again.sequence()).isEqualTo(first.sequence());
  }

  @Test
  void keepsTheOriginalSequenceOfAnItemAddedAgain() {
    queue.add("A");
    QueuedItem<String> first = queue.poll().orElseThrow();

    queue.add(first, Duration.ZERO);
    queue.add("B");

    assertThat(queue.poll()).map(QueuedItem::payload).contains("A");
    assertThat(queue.poll()).map(QueuedItem::payload).contains("B");
  }

  @Test
  void refusesNewItemOnceItHoldsItsCapacity() {
    queue.add("A");
    queue.add("B");

    assertThatThrownBy(() -> queue.add("C")).isInstanceOf(QueueFullException.class);
    assertThat(queue.size()).isEqualTo(CAPACITY);
  }

  @Test
  void addsAnItemBackEvenWhenItHoldsItsCapacity() {
    queue.add("A");
    QueuedItem<String> first = queue.poll().orElseThrow();
    queue.add("B");
    queue.add("C");

    queue.add(first, Duration.ZERO);

    assertThat(queue.size()).isEqualTo(CAPACITY + 1);
  }
}
