package com.outpost.framework.queue;

import java.time.Instant;

/**
 * One item held by a {@link TimeOrderedQueue}.
 *
 * @param notBefore the earliest time the item may be taken
 * @param sequence the arrival order; an item added again keeps its sequence
 * @param attempts how many times the item was handed to a handler before
 */
public record QueuedItem<T>(T payload, Instant notBefore, long sequence, int attempts) {}
