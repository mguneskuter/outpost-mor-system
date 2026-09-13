package com.outpost.framework.queue;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Settings of a {@link QueueProcessor}.
 *
 * @param workerCount threads that drain the queue
 * @param pollInterval delay between two drains of one thread
 * @param retryDelay delay before an item answered RETRY_LATER is due again
 * @param maxAttempts how many times one item is handed to the handler at most
 */
public record QueueProcessorSettings(
    int workerCount, Duration pollInterval, Duration retryDelay, int maxAttempts) {
  /**
   * Rejects settings a processor cannot run with.
   *
   * @throws IllegalArgumentException when {@code workerCount} or {@code maxAttempts} is below one,
   *     or {@code pollInterval} or {@code retryDelay} is null, zero, or negative
   */
  public QueueProcessorSettings {
    if (workerCount < 1) {
      throw new IllegalArgumentException("workerCount must be at least 1: " + workerCount);
    }
    requirePositive(pollInterval, "pollInterval");
    requirePositive(retryDelay, "retryDelay");
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
    }
  }

  private static void requirePositive(@Nullable Duration duration, String name) {
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive: " + duration);
    }
  }
}
