package com.outpost.ledger.readiness;

import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/** Publishes Ledger's traffic acceptance after all startup checks complete. */
@Configuration
public class LedgerReadinessConfiguration {

  private final ApplicationEventPublisher eventPublisher;

  LedgerReadinessConfiguration(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /** Marks the service ready after the application context has completed startup. */
  @EventListener(ApplicationReadyEvent.class)
  void acceptTraffic() {
    AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.ACCEPTING_TRAFFIC);
  }
}
