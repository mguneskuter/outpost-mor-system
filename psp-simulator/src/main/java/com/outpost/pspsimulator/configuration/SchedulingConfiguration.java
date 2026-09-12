package com.outpost.pspsimulator.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Configures the pool that delivers delayed webhook events. */
@Configuration
public class SchedulingConfiguration {

  /** Returns the scheduler webhook events are scheduled on. */
  @Bean
  public TaskScheduler webhookScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("psp-webhook-");
    scheduler.setPoolSize(4);
    return scheduler;
  }
}
