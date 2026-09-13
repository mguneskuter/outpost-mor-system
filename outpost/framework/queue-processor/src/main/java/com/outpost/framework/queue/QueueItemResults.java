package com.outpost.framework.queue;

/** What a {@link QueueItemHandler} asks the queue to do with an item after handling it. */
public enum QueueItemResults {
  /** The item is removed. */
  DONE,
  /**
   * The item is tried again after the retry delay, unless it has been tried the maximum number of
   * times.
   */
  RETRY_LATER
}
