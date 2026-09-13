package com.outpost.framework.queue.testfixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A UTC clock a test moves by hand. */
public final class MutableClock extends Clock {
  private Instant now;

  /** Creates a clock that reads {@code now} until it is advanced. */
  public MutableClock(Instant now) {
    this.now = now;
  }

  /** Moves the clock by {@code duration}, which may be negative. */
  public void advance(Duration duration) {
    now = now.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now;
  }
}
