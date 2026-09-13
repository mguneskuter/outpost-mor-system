package com.outpost.pspsimulator.webhook;

import com.outpost.pspsimulator.configuration.SimulatorProperties;
import com.outpost.pspsimulator.configuration.SimulatorProperties.DelaySettings;
import com.outpost.pspsimulator.order.Order;
import com.outpost.pspsimulator.order.OrderRepository;
import com.outpost.pspsimulator.order.OrderStatuses;
import com.outpost.pspsimulator.order.ResultCodes;
import com.outpost.pspsimulator.refund.Refund;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/** Schedules webhook events after their configured delays. */
@Component
public final class WebhookScheduler {

  private final TaskScheduler taskScheduler;
  private final WebhookDispatcher dispatcher;
  private final OrderRepository orderRepository;
  private final DelaySettings delays;

  /** Creates a scheduler using the configured event delays and delivery adapter. */
  public WebhookScheduler(
      TaskScheduler taskScheduler,
      WebhookDispatcher dispatcher,
      OrderRepository orderRepository,
      SimulatorProperties properties) {
    this.taskScheduler = taskScheduler;
    this.dispatcher = dispatcher;
    this.orderRepository = orderRepository;
    this.delays = properties.delays();
  }

  /**
   * Schedules the AUTHORISATION webhook, and the CAPTURE webhook when the payment was approved. The
   * capture is only reported if the order is still authorised when the capture delay elapses.
   */
  public void scheduleAuthorisation(Order order, ResultCodes outcome) {
    boolean approved = outcome == ResultCodes.APPROVED;
    Instant authorisationAt = Instant.now().plus(authorisationDelay());
    taskScheduler.schedule(authorisationEvent(order, outcome), authorisationAt);
    if (approved) {
      taskScheduler.schedule(captureEvent(order), authorisationAt.plus(delays.capture()));
    }
  }

  /** Schedules the REFUND webhook for an accepted refund. */
  public void scheduleRefund(Order order, Refund refund) {
    taskScheduler.schedule(refundEvent(order, refund), Instant.now().plus(delays.refund()));
  }

  private Runnable authorisationEvent(Order order, ResultCodes outcome) {
    return () -> dispatcher.dispatch(eventPayload(order, WebhookEventCodes.AUTHORISATION, outcome));
  }

  private Runnable captureEvent(Order order) {
    return () -> {
      boolean captured =
          orderRepository.transition(
              order.pspCode(),
              order.pspReference(),
              OrderStatuses.AUTHORISED,
              OrderStatuses.CAPTURED);
      if (captured) {
        dispatcher.dispatch(eventPayload(order, WebhookEventCodes.CAPTURE, ResultCodes.APPROVED));
      }
    };
  }

  private Runnable refundEvent(Order order, Refund refund) {
    return () ->
        dispatcher.dispatch(
            new WebhookPayload(
                order.pspCode(),
                Long.toString(order.pspReference()),
                Long.toString(refund.pspRefundReference()),
                order.paymentReference(),
                WebhookEventCodes.REFUND,
                Instant.now().getEpochSecond(),
                true,
                ResultCodes.APPROVED,
                refund.amountMinor(),
                refund.currencyCode(),
                refund.refundReference()));
  }

  private static WebhookPayload eventPayload(
      Order order, WebhookEventCodes eventCode, ResultCodes resultCode) {
    return new WebhookPayload(
        order.pspCode(),
        Long.toString(order.pspReference()),
        null,
        order.paymentReference(),
        eventCode,
        Instant.now().getEpochSecond(),
        resultCode == ResultCodes.APPROVED,
        resultCode,
        order.amountMinor(),
        order.currencyCode(),
        null);
  }

  private Duration authorisationDelay() {
    long min = delays.authorisationMin().toMillis();
    long span = delays.authorisationMax().toMillis() - min;
    if (span == 0) {
      return delays.authorisationMin();
    }
    return Duration.ofMillis(min + ThreadLocalRandom.current().nextLong(span + 1));
  }
}
