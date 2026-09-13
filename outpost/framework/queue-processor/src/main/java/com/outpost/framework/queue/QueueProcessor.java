package com.outpost.framework.queue;

import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Drains a {@link TimeOrderedQueue} with a pool of worker threads, handing each due item to a
 * {@link QueueItemHandler} and re-queueing or dropping items the handler asks to retry. The
 * processor logs no payload field.
 */
public final class QueueProcessor<T> {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(QueueProcessor.class));
  private static final long STOP_TIMEOUT_SECONDS = 10;

  private final String name;
  private final TimeOrderedQueue<T> queue;
  private final QueueItemHandler<T> handler;
  private final QueueProcessorSettings settings;
  private @Nullable ScheduledThreadPoolExecutor executor;

  /** Creates a stopped processor; {@link #start()} creates its worker threads. */
  public QueueProcessor(
      String name,
      TimeOrderedQueue<T> queue,
      QueueItemHandler<T> handler,
      QueueProcessorSettings settings) {
    this.name = name;
    this.queue = queue;
    this.handler = handler;
    this.settings = settings;
  }

  /** Starts the worker threads. Calling it again while running does nothing. */
  public synchronized void start() {
    if (executor != null) {
      return;
    }
    ScheduledThreadPoolExecutor started =
        new ScheduledThreadPoolExecutor(settings.workerCount(), new NamedThreadFactory(name));
    for (int worker = 0; worker < settings.workerCount(); worker++) {
      ScheduledFuture<?> unused =
          started.scheduleWithFixedDelay(
              this::drain, 0, settings.pollInterval().toMillis(), TimeUnit.MILLISECONDS);
    }
    executor = started;
  }

  /**
   * Stops the worker threads, waiting for a running handler to finish. Items still queued are lost.
   */
  public void stop() {
    ScheduledThreadPoolExecutor running;
    synchronized (this) {
      running = executor;
      executor = null;
    }
    if (running == null) {
      return;
    }
    running.shutdown();
    try {
      if (!running.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        running.shutdownNow();
      }
    } catch (InterruptedException interrupted) {
      running.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void drain() {
    while (true) {
      Optional<QueuedItem<T>> due = queue.poll();
      if (due.isEmpty()) {
        return;
      }
      QueuedItem<T> item = due.get();
      QueueItemResults result;
      try {
        result = handler.handle(item.payload());
      } catch (RuntimeException exception) {
        LOGGER.error(
            "Queue item handling failed", exception, queueField(), attemptsField(item.attempts()));
        result = QueueItemResults.RETRY_LATER;
      }
      if (result == QueueItemResults.RETRY_LATER) {
        int attempts = item.attempts() + 1;
        if (attempts >= settings.maxAttempts()) {
          LOGGER.error(
              "Queue item dropped",
              new QueueItemDroppedException(),
              queueField(),
              attemptsField(attempts));
        } else {
          queue.add(item, settings.retryDelay());
        }
      }
    }
  }

  private StructuredLogField queueField() {
    return new StructuredLogField(LogField.QUEUE, name);
  }

  private static StructuredLogField attemptsField(int attempts) {
    return new StructuredLogField(LogField.ATTEMPTS, Integer.toString(attempts));
  }

  /** Carries the drop into the error log, which records only throwables. */
  private static final class QueueItemDroppedException extends RuntimeException {}

  private static final class NamedThreadFactory implements ThreadFactory {
    private final String name;
    private final AtomicInteger nextNumber = new AtomicInteger(1);

    private NamedThreadFactory(String name) {
      this.name = name;
    }

    @Override
    public Thread newThread(Runnable task) {
      Thread thread = new Thread(task, name + "-" + nextNumber.getAndIncrement());
      thread.setDaemon(false);
      return thread;
    }
  }

  private enum LogField implements LogFields {
    QUEUE("queue"),
    ATTEMPTS("attempts");

    private final String jsonKey;

    LogField(String jsonKey) {
      this.jsonKey = jsonKey;
    }

    @Override
    public String getJsonKey() {
      return jsonKey;
    }
  }
}
