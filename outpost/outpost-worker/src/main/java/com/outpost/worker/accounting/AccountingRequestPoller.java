package com.outpost.worker.accounting;

import com.outpost.accounting.queue.AccountingRequest;
import com.outpost.accounting.queue.AccountingRequestQueue;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;

/** Runs accounting request processing on its own bounded worker pool. */
public final class AccountingRequestPoller implements SmartLifecycle {
  private final AccountingRequestProcessor processor;
  private final ThreadPoolExecutor executor;
  private final long pollIntervalMillis;
  private final Object monitor = new Object();
  private boolean claiming;

  /** Creates a poller with the configured worker count and idle interval. */
  public AccountingRequestPoller(
      AccountingRequestProcessor processor, int workerCount, Duration pollInterval) {
    if (workerCount <= 0) {
      throw new IllegalArgumentException("workerCount must be positive");
    }
    if (pollInterval.isNegative() || pollInterval.isZero()) {
      throw new IllegalArgumentException("pollInterval must be positive");
    }
    this.processor = processor;
    executor =
        new ThreadPoolExecutor(
            workerCount,
            workerCount,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(workerCount),
            new ThreadPoolExecutor.AbortPolicy());
    pollIntervalMillis = pollInterval.toMillis();
    if (pollIntervalMillis == 0) {
      throw new IllegalArgumentException("pollInterval must be at least one millisecond");
    }
  }

  @Override
  public void start() {
    synchronized (monitor) {
      if (claiming) {
        return;
      }
      claiming = true;
      for (int index = 0; index < executor.getCorePoolSize(); index++) {
        executor.execute(this::poll);
      }
    }
  }

  @Override
  public void stop() {
    synchronized (monitor) {
      claiming = false;
      monitor.notifyAll();
    }
    executor.shutdown();
    boolean interrupted = false;
    while (!executor.isTerminated()) {
      try {
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
      } catch (InterruptedException exception) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }

  @Override
  public boolean isRunning() {
    synchronized (monitor) {
      return claiming;
    }
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public int getPhase() {
    return 0;
  }

  private void poll() {
    while (canClaim()) {
      if (!processor.processNext(this::claimNextWhenRunning)) {
        awaitNextPoll();
      }
    }
  }

  private boolean canClaim() {
    synchronized (monitor) {
      return claiming;
    }
  }

  private Optional<AccountingRequest> claimNextWhenRunning(AccountingRequestQueue requests) {
    synchronized (monitor) {
      if (!claiming) {
        return Optional.empty();
      }
      return requests.claimNext();
    }
  }

  private void awaitNextPoll() {
    synchronized (monitor) {
      if (!claiming) {
        return;
      }
      long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(pollIntervalMillis);
      long remainingNanos = deadlineNanos - System.nanoTime();
      while (claiming && remainingNanos > 0) {
        try {
          long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
          int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
          monitor.wait(millis, nanos);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return;
        }
        remainingNanos = deadlineNanos - System.nanoTime();
      }
    }
  }
}
