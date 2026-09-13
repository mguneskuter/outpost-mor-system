package com.outpost.framework.queue;

/** Handles the items a {@link QueueProcessor} takes from its queue. */
@FunctionalInterface
public interface QueueItemHandler<T> {
  /**
   * Handles one due item. A thrown {@link RuntimeException} counts as {@link
   * QueueItemResults#RETRY_LATER}.
   */
  QueueItemResults handle(T payload);
}
