package com.outpost.framework.queue;

/** A new item was offered to a {@link TimeOrderedQueue} that already holds its capacity. */
public final class QueueFullException extends IllegalStateException {}
