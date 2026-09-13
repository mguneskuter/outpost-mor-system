package com.outpost.pspsimulator.configuration;

import com.outpost.pspsimulator.order.OrderRepository;
import com.outpost.pspsimulator.order.OrderService;
import com.outpost.pspsimulator.refund.RefundRepository;
import com.outpost.pspsimulator.refund.RefundService;
import com.outpost.pspsimulator.webhook.WebhookScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the order and refund policies that the simulator's PSP API depends on. */
@Configuration(proxyBeanMethods = false)
public class ApplicationBeanConfiguration {

  @Bean
  OrderService orderService(
      OrderRepository orderRepository,
      WebhookScheduler webhookScheduler,
      SimulatorProperties properties) {
    return new OrderService(orderRepository, webhookScheduler, properties);
  }

  @Bean
  RefundService refundService(
      OrderRepository orderRepository,
      RefundRepository refundRepository,
      WebhookScheduler webhookScheduler) {
    return new RefundService(orderRepository, refundRepository, webhookScheduler);
  }
}
