package com.outpost.framework.queue;

/** Processes the items a {@link QueueProcessor} takes from its queue. */
@FunctionalInterface
public interface QueueItemProcessor<T> {
  /**
   * Processes one due item. A thrown {@link RuntimeException} counts as {@link
   * QueueItemResults#RETRY_LATER}.
   */
  QueueItemResults process(T payload);
}
