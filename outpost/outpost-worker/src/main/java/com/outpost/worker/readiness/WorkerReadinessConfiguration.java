package com.outpost.worker.readiness;

import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/** Publishes Worker readiness after startup validation completes. */
@Configuration
public class WorkerReadinessConfiguration {
  private final ApplicationEventPublisher eventPublisher;

  WorkerReadinessConfiguration(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /** Marks the worker ready after the application context has completed startup. */
  @EventListener(ApplicationReadyEvent.class)
  void acceptTraffic() {
    AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.ACCEPTING_TRAFFIC);
  }
}
