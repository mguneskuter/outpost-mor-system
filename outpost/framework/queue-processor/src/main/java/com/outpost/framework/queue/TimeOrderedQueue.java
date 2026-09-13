package com.outpost.framework.queue;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * A queue whose items are taken in order of the time they become due, then in arrival order. The
 * queue is held in memory only and refuses a new item once it holds its capacity. All methods are
 * thread-safe.
 */
public final class TimeOrderedQueue<T> {
  private final Clock clock;
  private final int capacity;
  private final PriorityQueue<QueuedItem<T>> items =
      new PriorityQueue<>(
          Comparator.comparing(QueuedItem<T>::notBefore).thenComparingLong(QueuedItem::sequence));
  private long nextSequence;

  /**
   * Creates an empty queue that reads due times from {@code clock} and accepts a new item while it
   * holds fewer than {@code capacity} items.
   *
   * @throws IllegalArgumentException when {@code capacity} is below one
   */
  public TimeOrderedQueue(Clock clock, int capacity) {
    this.clock = Objects.requireNonNull(clock, "clock");
    if (capacity < 1) {
      throw new IllegalArgumentException("Capacity must be at least 1: " + capacity);
    }
    this.capacity = capacity;
  }

  /**
   * Adds an item that is due now.
   *
   * @throws QueueFullException when the queue already holds its capacity
   */
  public synchronized void add(T payload) {
    Objects.requireNonNull(payload, "payload");
    if (items.size() >= capacity) {
      throw new QueueFullException();
    }
    items.add(new QueuedItem<>(payload, clock.instant(), nextSequence++, 0));
  }

  /**
   * Adds an item back, due after {@code delay}, keeping its sequence and counting the attempt it
   * has just been through. The item was taken from this queue, so it is added even when the queue
   * holds its capacity.
   */
  public synchronized void add(QueuedItem<T> item, Duration delay) {
    items.add(
        new QueuedItem<>(
            item.payload(), clock.instant().plus(delay), item.sequence(), item.attempts() + 1));
  }

  /** Removes and returns the first item, when it is due; empty otherwise. */
  public synchronized Optional<QueuedItem<T>> poll() {
    return Optional.ofNullable(items.peek())
        .filter(head -> !head.notBefore().isAfter(clock.instant()))
        .map(head -> items.remove());
  }

  /** Returns the number of items held, due or not. */
  public synchronized int size() {
    return items.size();
  }
}
