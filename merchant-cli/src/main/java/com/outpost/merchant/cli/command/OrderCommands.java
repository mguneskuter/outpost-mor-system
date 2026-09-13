package com.outpost.merchant.cli.command;

import com.outpost.merchant.cli.configuration.MerchantCliProperties;
import com.outpost.merchant.cli.configuration.MerchantCliProperties.CatalogueItem;
import com.outpost.merchant.cli.gateway.CreateOrderRequest;
import com.outpost.merchant.cli.gateway.CreatedOrder;
import com.outpost.merchant.cli.gateway.GatewayClient;
import com.outpost.merchant.cli.gateway.GatewayException;
import com.outpost.merchant.cli.merchant.MerchantRepository;
import com.outpost.merchant.cli.merchant.OrderEvent;
import com.outpost.merchant.cli.merchant.OrderPayment;
import com.outpost.merchant.cli.psp.PspPaymentClient;
import com.outpost.merchant.cli.psp.PspPaymentException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

/** Commands that take an order through its life: create, pay, refund. */
public class OrderCommands {
  /** The simulator's approved test card. */
  static final String APPROVED_CARD = "4111111111111111";

  private static final Duration OUTCOME_POLL_INTERVAL = Duration.ofMillis(500);

  private final Map<String, CatalogueItem> catalogue;
  private final GatewayClient gateway;
  private final PspPaymentClient psp;
  private final MerchantRepository merchants;
  private final ShellSession session;
  private final Duration outcomeWait;

  /**
   * Creates the commands over the catalogue, the Gateway, the PSP, and the session; {@code pay}
   * waits up to {@code outcomeWait} for the Ledger to book the payment's outcome.
   */
  public OrderCommands(
      MerchantCliProperties properties,
      GatewayClient gateway,
      PspPaymentClient psp,
      MerchantRepository merchants,
      ShellSession session,
      Duration outcomeWait) {
    this.catalogue =
        properties.catalogue().stream()
            .collect(Collectors.toMap(CatalogueItem::sku, Function.identity()));
    this.gateway = gateway;
    this.psp = psp;
    this.merchants = merchants;
    this.session = session;
    this.outcomeWait = outcomeWait;
  }

  /** Lists what the merchant sells. */
  @Command(
      group = "Order",
      name = "catalogue",
      description = "List the items an order can be built from")
  public String catalogue() {
    StringJoiner lines = new StringJoiner("\n");
    catalogue.values().stream()
        .sorted((a, b) -> a.sku().compareTo(b.sku()))
        .forEach(
            item ->
                lines.add(
                    item.sku()
                        + "  "
                        + item.name()
                        + "  "
                        + Money.format(item.amount(), item.currency())
                        + "  "
                        + item.type()));
    return lines.toString();
  }

  /** Creates an order for the current merchant from catalogue items and a shopper. */
  @Command(
      group = "Order",
      name = "order",
      description = "Create an order from catalogue items for a shopper")
  public String order(
      @Option(longName = "psp", required = true, description = "PSP code, see psps") String pspCode,
      @Option(
              longName = "items",
              required = true,
              description = "comma-separated catalogue SKUs, see catalogue")
          String items,
      @Option(longName = "country", defaultValue = "NL", description = "shopper country, ISO code")
          String country,
      @Option(longName = "state", description = "shopper subdivision, e.g. US-CA")
          @Nullable String state,
      @Option(longName = "email", defaultValue = "shopper@example.test") String email,
      @Option(longName = "name", defaultValue = "Test Shopper") String fullName) {
    List<CreateOrderRequest.Line> lines = new ArrayList<>();
    String currency = "";
    long total = 0;
    for (String sku : items.split(",", -1)) {
      CatalogueItem item = catalogue.get(sku.trim());
      if (item == null) {
        return "unknown catalogue item " + sku.trim() + "; see catalogue";
      }
      lines.add(
          new CreateOrderRequest.Line(item.sku(), item.amount(), item.currency(), item.type()));
      currency = item.currency();
      total += item.amount();
    }
    String reference = "cli-" + UUID.randomUUID();
    CreateOrderRequest request =
        new CreateOrderRequest(
            reference,
            reference,
            pspCode,
            new CreateOrderRequest.Shopper(fullName, email, country, state, null),
            new CreateOrderRequest.OrderDetails(lines, total, currency));
    CreatedOrder order;
    try {
      order = gateway.createOrder(session.credentials(), request);
    } catch (GatewayException refused) {
      return refused.describe();
    }
    return format(order);
  }

  /**
   * Pays an order at the PSP with a test card, then reports the outcome the Ledger books: the PSP
   * answers by webhook, so the outcome is what Outpost booked for the order once it arrived.
   */
  @Command(
      group = "Order",
      name = "pay",
      description = "Pay an order at its PSP with a test card and report the booked outcome")
  public String pay(
      @Argument(index = 0, description = "order reference") String orderReference,
      @Option(
              longName = "card",
              defaultValue = APPROVED_CARD,
              description =
                  "test card: 4111111111111111 approved, 4000000000000002 refused, "
                      + "4000000000000009 scheme error")
          String card) {
    Optional<OrderPayment> payment;
    try {
      payment = merchants.findOrderPayment(orderReference);
    } catch (DataAccessException unreadable) {
      return Database.unreadable(unreadable);
    }
    if (payment.isEmpty()) {
      return "no payment to pay for " + orderReference + "; create the order first";
    }
    try {
      psp.pay(payment.orElseThrow().paymentLink(), payment.orElseThrow().pspReference(), card);
    } catch (PspPaymentException refused) {
      return "payment not accepted: " + refused.reason();
    }
    return "payment submitted for " + orderReference + "; " + awaitOutcome(orderReference);
  }

  /** Lists what the Ledger booked for an order so far. */
  @Command(
      group = "Order",
      name = "status",
      description = "List what Outpost booked for an order: payment, capture, and refund events")
  public String status(
      @Argument(index = 0, description = "order reference") String orderReference) {
    List<OrderEvent> events;
    try {
      events = merchants.findOrderEvents(orderReference);
    } catch (DataAccessException unreadable) {
      return Database.unreadable(unreadable);
    }
    if (events.isEmpty()) {
      return "nothing booked yet for " + orderReference;
    }
    StringJoiner lines = new StringJoiner("\n");
    for (OrderEvent event : events) {
      lines.add(event.transactionType() + "  " + event.eventType() + "  " + event.occurredAt());
    }
    return lines.toString();
  }

  /**
   * Waits for the Ledger to book the PSP's authorisation outcome and, when authorised, the capture.
   */
  private String awaitOutcome(String orderReference) {
    long deadline = System.nanoTime() + outcomeWait.toNanos();
    boolean authorised = false;
    while (true) {
      List<OrderEvent> events;
      try {
        events = merchants.findOrderEvents(orderReference);
      } catch (DataAccessException unreadable) {
        return Database.unreadable(unreadable);
      }
      if (has(events, "REFUSED")) {
        return "refused by the PSP";
      }
      if (has(events, "CAPTURED")) {
        return "authorised and captured";
      }
      if (has(events, "CAPTURE_FAILED")) {
        return "authorised, but the capture failed";
      }
      authorised = has(events, "AUTHORISED");
      if (System.nanoTime() >= deadline) {
        break;
      }
      try {
        Thread.sleep(OUTCOME_POLL_INTERVAL);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return "interrupted while waiting for the outcome; see status " + orderReference;
      }
    }
    String waited = "within " + outcomeWait.toMillis() + " ms";
    return authorised
        ? "authorised; the capture was not booked " + waited + "; see status " + orderReference
        : "no outcome booked " + waited + "; see status " + orderReference;
  }

  private static boolean has(List<OrderEvent> events, String eventType) {
    return events.stream().anyMatch(event -> event.eventType().equals(eventType));
  }

  /** Refunds the whole order. */
  @Command(group = "Order", name = "refund", description = "Refund the whole order at its PSP")
  public String refund(
      @Argument(index = 0, description = "order reference") String orderReference) {
    try {
      String refundReference =
          gateway.refund(session.credentials(), orderReference, "refund-" + UUID.randomUUID());
      return "refund accepted: " + refundReference;
    } catch (GatewayException refused) {
      return refused.describe();
    }
  }

  private static String format(CreatedOrder order) {
    CreatedOrder.PaymentDetails payment = order.paymentDetails();
    StringJoiner lines = new StringJoiner("\n");
    lines.add("order " + order.orderReference());
    for (CreatedOrder.Line line : order.orderLines()) {
      lines.add(
          "  "
              + line.merchantLineReference()
              + "  net "
              + Money.format(line.amount(), payment.currency())
              + "  tax "
              + Money.format(line.taxAmount(), payment.currency())
              + " (rate "
              + line.taxRate()
              + ")");
    }
    lines.add(
        "net "
            + Money.format(payment.amount(), payment.currency())
            + "  tax "
            + Money.format(payment.taxAmount(), payment.currency())
            + "  total "
            + Money.format(payment.totalAmount(), payment.currency()));
    lines.add("pay at " + payment.paymentLink() + "  (pay " + order.orderReference() + ")");
    return lines.toString();
  }
}
