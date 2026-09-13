package com.outpost.framework.queue;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * A queue whose items are taken in order of the time they become due, then in arrival order. The
 * queue is unbounded and held in memory only. All methods are thread-safe.
 */
public final class TimeOrderedQueue<T> {
  private final Clock clock;
  private final PriorityQueue<QueuedItem<T>> items =
      new PriorityQueue<>(
          Comparator.comparing(QueuedItem<T>::notBefore).thenComparingLong(QueuedItem::sequence));
  private long nextSequence;

  /** Creates an empty queue that reads due times from {@code clock}. */
  public TimeOrderedQueue(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Adds an item that is due now. */
  public synchronized void add(T payload) {
    Objects.requireNonNull(payload, "payload");
    items.add(new QueuedItem<>(payload, clock.instant(), nextSequence++, 0));
  }

  /**
   * Adds an item back, due after {@code delay}, keeping its sequence and counting the attempt it
   * has just been through.
   */
  public synchronized void addAgain(QueuedItem<T> item, Duration delay) {
    items.add(
        new QueuedItem<>(
            item.payload(), clock.instant().plus(delay), item.sequence(), item.attempts() + 1));
  }

  /** Removes and returns the first item, when it is due; empty otherwise. */
  public synchronized Optional<QueuedItem<T>> pollDue() {
    QueuedItem<T> head = items.peek();
    return head != null && !head.notBefore().isAfter(clock.instant())
        ? Optional.of(items.poll())
        : Optional.empty();
  }

  /** Returns the number of items held, due or not. */
  public synchronized int size() {
    return items.size();
  }
}
