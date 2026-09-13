package com.outpost.pspsimulator.order;

import com.outpost.pspsimulator.ConflictException;
import com.outpost.pspsimulator.NotFoundException;
import com.outpost.pspsimulator.configuration.SimulatorProperties;
import com.outpost.pspsimulator.webhook.WebhookScheduler;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Drives an order through creation and payment. */
public final class OrderService {
  private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);

  private final OrderRepository orderRepository;
  private final WebhookScheduler webhookScheduler;
  private final String publicUrl;

  /** Creates an order service with persistence, webhook scheduling, and payment URL settings. */
  public OrderService(
      OrderRepository orderRepository,
      WebhookScheduler webhookScheduler,
      SimulatorProperties properties) {
    this.orderRepository = orderRepository;
    this.webhookScheduler = webhookScheduler;
    this.publicUrl = properties.publicUrl();
  }

  /**
   * Creates an order, or returns the existing one when the payment reference repeats.
   *
   * @throws ConflictException when the payment reference repeats with different data
   */
  public CreateOrderResult createOrder(String pspCode, CreateOrderCommand command) {
    Optional<Order> inserted =
        orderRepository.insert(
            pspCode, command.paymentReference(), command.amountMinor(), command.currencyCode());
    if (inserted.isPresent()) {
      Order order = inserted.get();
      LOGGER.info(
          "order created pspCode={} pspReference={} paymentReference={} amount={} currency={}",
          pspCode,
          order.pspReference(),
          order.paymentReference(),
          order.amountMinor(),
          order.currencyCode());
      return new CreateOrderResult(order, paymentUrl(pspCode), true);
    }
    Order existing =
        orderRepository
            .findByPaymentReference(pspCode, command.paymentReference())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "payment reference claimed but not persisted: "
                            + command.paymentReference()));
    if (existing.amountMinor() != command.amountMinor()
        || !existing.currencyCode().equals(command.currencyCode())) {
      throw new ConflictException(
          "payment reference " + command.paymentReference() + " was used with different data");
    }
    LOGGER.info(
        "order repeated pspCode={} pspReference={} paymentReference={}",
        pspCode,
        existing.pspReference(),
        existing.paymentReference());
    return new CreateOrderResult(existing, paymentUrl(pspCode), false);
  }

  /**
   * Submits a card number against an order and schedules the authorisation, and capture when
   * approved.
   *
   * @throws NotFoundException when the order is unknown
   * @throws ConflictException when the order was already paid
   */
  public void pay(String pspCode, PayCommand command) {
    Order order =
        orderRepository
            .findByPspReference(pspCode, command.pspReference())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "no order with psp reference: " + command.pspReference()));
    ResultCodes outcome =
        TestCards.outcomeFor(command.cardNumber())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "unknown test card number: " + command.cardNumber()));
    OrderStatuses next =
        outcome == ResultCodes.APPROVED ? OrderStatuses.AUTHORISED : OrderStatuses.REFUSED;
    boolean transitioned =
        orderRepository.transition(pspCode, command.pspReference(), OrderStatuses.CREATED, next);
    if (!transitioned) {
      throw new ConflictException("order no longer awaits payment: " + command.pspReference());
    }
    LOGGER.info(
        "payment submitted pspCode={} pspReference={} paymentReference={} outcome={}",
        pspCode,
        order.pspReference(),
        order.paymentReference(),
        outcome.getCode());
    webhookScheduler.scheduleAuthorisation(order, outcome);
  }

  private String paymentUrl(String pspCode) {
    return publicUrl + "/v1/" + pspCode + "/payment";
  }

  /** The result of a create-order command: the order, its payment URL, and whether it is new. */
  public record CreateOrderResult(Order order, String paymentUrl, boolean created) {}
}
