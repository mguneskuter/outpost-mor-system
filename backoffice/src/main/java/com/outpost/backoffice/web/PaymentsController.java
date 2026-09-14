package com.outpost.backoffice.web;

import com.outpost.backoffice.configuration.BackOfficeProperties;
import com.outpost.backoffice.configuration.BackOfficeProperties.CatalogueItem;
import com.outpost.backoffice.configuration.BackOfficeProperties.MerchantCredentials;
import com.outpost.backoffice.gateway.CreateOrderRequest;
import com.outpost.backoffice.gateway.CreatedOrder;
import com.outpost.backoffice.gateway.GatewayClient;
import com.outpost.backoffice.gateway.GatewayException;
import com.outpost.backoffice.merchant.Merchant;
import com.outpost.backoffice.merchant.MerchantRepository;
import com.outpost.backoffice.payment.OrderLine;
import com.outpost.backoffice.payment.Payment;
import com.outpost.backoffice.payment.PaymentEvent;
import com.outpost.backoffice.payment.PaymentJournalLine;
import com.outpost.backoffice.payment.PaymentRepository;
import com.outpost.backoffice.psp.PspPaymentClient;
import com.outpost.backoffice.psp.PspPaymentException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The payments tab: create and pay an order as a merchant, refund one, and list them all. */
@Controller
public class PaymentsController {
  /** The simulator's test cards and the outcome each selects. */
  static final List<Card> CARDS =
      List.of(
          new Card("4111111111111111", "approved"),
          new Card("4000000000000002", "refused by the acquirer"),
          new Card("4000000000000009", "scheme error"));

  private final BackOfficeProperties properties;
  private final MerchantRepository merchants;
  private final PaymentRepository payments;
  private final GatewayClient gateway;
  private final PspPaymentClient psp;

  /** Creates the controller over the stored merchants and payments, the Gateway, and the PSP. */
  public PaymentsController(
      BackOfficeProperties properties,
      MerchantRepository merchants,
      PaymentRepository payments,
      GatewayClient gateway,
      PspPaymentClient psp) {
    this.properties = properties;
    this.merchants = merchants;
    this.payments = payments;
    this.gateway = gateway;
    this.psp = psp;
  }

  /** The payments tab is the front page. */
  @GetMapping("/")
  public String home() {
    return "redirect:/payments";
  }

  /** Shows the order form for the chosen merchant and every payment. */
  @GetMapping("/payments")
  public String payments(@RequestParam(required = false) @Nullable String merchant, Model model) {
    List<Merchant> usable =
        merchants.findActiveMerchants().stream()
            .filter(candidate -> properties.merchants().containsKey(candidate.code()))
            .toList();
    String selected =
        usable.stream()
            .map(Merchant::code)
            .filter(code -> code.equals(merchant))
            .findFirst()
            .orElse(usable.isEmpty() ? "" : usable.getFirst().code());
    model.addAttribute("tab", "payments");
    model.addAttribute("merchants", usable);
    model.addAttribute("selectedMerchant", selected);
    model.addAttribute(
        "psps", selected.isEmpty() ? List.of() : merchants.findEnabledPsps(selected));
    model.addAttribute("countries", merchants.findCountryCodes());
    model.addAttribute(
        "catalogue",
        properties.catalogue().stream()
            .map(
                item ->
                    new CatalogueOption(
                        item.sku(),
                        item.name()
                            + " – "
                            + Money.format(item.amount())
                            + " "
                            + item.currency()
                            + " – "
                            + item.type()))
            .toList());
    model.addAttribute("cards", CARDS);
    model.addAttribute("payments", payments.findPayments().stream().map(PaymentRow::of).toList());
    return "payments";
  }

  /** Creates the order at the Gateway and pays it at the PSP with the chosen card. */
  @PostMapping("/payments")
  public String createAndPay(
      @RequestParam String merchant,
      @RequestParam String psp,
      @RequestParam(required = false) @Nullable List<String> items,
      @RequestParam String country,
      @RequestParam(required = false) @Nullable String state,
      @RequestParam String card,
      RedirectAttributes redirect) {
    redirect.addAttribute("merchant", merchant);
    MerchantCredentials credentials = properties.merchants().get(merchant);
    if (credentials == null) {
      return failed(redirect, "no credentials configured for " + merchant);
    }
    Map<String, CatalogueItem> catalogue = new java.util.HashMap<>();
    properties.catalogue().forEach(item -> catalogue.put(item.sku(), item));
    List<CreateOrderRequest.Line> lines = new ArrayList<>();
    String currency = "";
    long total = 0;
    for (String sku : items == null ? List.<String>of() : items) {
      CatalogueItem item = catalogue.get(sku);
      if (item == null) {
        return failed(redirect, "unknown catalogue item " + sku);
      }
      lines.add(
          new CreateOrderRequest.Line(item.sku(), item.amount(), item.currency(), item.type()));
      currency = item.currency();
      total += item.amount();
    }
    if (lines.isEmpty()) {
      return failed(redirect, "pick at least one item");
    }
    String reference = "office-" + UUID.randomUUID();
    CreatedOrder order;
    try {
      order =
          gateway.createOrder(
              credentials,
              new CreateOrderRequest(
                  reference,
                  reference,
                  psp,
                  new CreateOrderRequest.Shopper(
                      "Back Office Shopper",
                      "shopper@example.test",
                      country,
                      state == null || state.isBlank() ? null : state,
                      null),
                  new CreateOrderRequest.OrderDetails(lines, total, currency)));
    } catch (GatewayException refused) {
      return failed(redirect, "order refused: " + refused.describe());
    }
    String pspReference = order.paymentDetails().paymentLink();
    Payment stored =
        payments.findPayments().stream()
            .filter(payment -> payment.orderReference().equals(order.orderReference()))
            .findFirst()
            .orElse(null);
    if (stored == null || stored.pspReference() == null) {
      return failed(redirect, "order " + order.orderReference() + " has no PSP reference to pay");
    }
    try {
      this.psp.pay(pspReference, stored.pspReference(), card);
    } catch (PspPaymentException notAccepted) {
      return failed(redirect, "payment not accepted: " + notAccepted.reason());
    }
    redirect.addFlashAttribute(
        "message",
        "order "
            + order.orderReference()
            + " created and paid; the PSP reports the outcome by"
            + " webhook, reload to see it booked");
    return "redirect:/payments";
  }

  /** Shows what the Ledger booked for one payment: its events, journal lines, and registers. */
  @GetMapping("/payments/{orderReference}")
  public String breakdown(@PathVariable String orderReference, Model model) {
    Payment payment =
        payments.findPayments().stream()
            .filter(candidate -> candidate.orderReference().equals(orderReference))
            .findFirst()
            .orElse(null);
    if (payment == null) {
      return "redirect:/payments";
    }
    List<PaymentJournalLine> lines = payments.findPaymentJournalLines(orderReference);
    Map<String, RegisterMovement> registers = new LinkedHashMap<>();
    Map<String, RegisterMovement> perCurrency = new LinkedHashMap<>();
    for (PaymentJournalLine line : lines) {
      perCurrency.merge(
          line.currency(),
          new RegisterMovement(
              "",
              "",
              "",
              line.currency(),
              Math.max(line.quantity(), 0),
              Math.max(-line.quantity(), 0)),
          RegisterMovement::plus);
      registers.merge(
          line.accountCode() + "/" + line.registerType() + "/" + line.currency(),
          new RegisterMovement(
              line.accountCode(),
              line.accountName(),
              line.registerType(),
              line.currency(),
              line.quantity() > 0 ? line.quantity() : 0,
              line.quantity() < 0 ? -line.quantity() : 0),
          RegisterMovement::plus);
    }
    List<OrderLineRow> orderLines =
        payments.findOrderLines(orderReference).stream().map(OrderLineRow::of).toList();
    model.addAttribute("tab", "payments");
    model.addAttribute("payment", PaymentRow.of(payment));
    model.addAttribute("orderLines", orderLines);
    model.addAttribute("hasOpenLines", orderLines.stream().anyMatch(line -> !line.refunded()));
    model.addAttribute(
        "events", payments.findPaymentEvents(orderReference).stream().map(EventRow::of).toList());
    model.addAttribute("lines", lines.stream().map(LineRow::of).toList());
    model.addAttribute(
        "balanceAccounts", registers.values().stream().map(RegisterRow::of).toList());
    model.addAttribute("totals", perCurrency.values().stream().map(RegisterRow::of).toList());
    return "payment";
  }

  /**
   * Refunds the named order lines at the PSP, or every line not yet refunded when none is named;
   * {@code origin=payment} returns to the payment's page instead of the list.
   */
  @PostMapping("/payments/{orderReference}/refund")
  public String refund(
      @PathVariable String orderReference,
      @RequestParam String merchant,
      @RequestParam(required = false) @Nullable List<String> lines,
      @RequestParam(required = false) @Nullable String origin,
      RedirectAttributes redirect) {
    String target = "payment".equals(origin) ? "/payments/" + orderReference : "/payments";
    redirect.addAttribute("merchant", merchant);
    MerchantCredentials credentials = properties.merchants().get(merchant);
    if (credentials == null) {
      return failed(redirect, target, "no credentials configured for " + merchant);
    }
    try {
      String refundReference =
          gateway.refund(
              credentials,
              orderReference,
              "refund-" + UUID.randomUUID(),
              lines == null ? List.of() : lines);
      redirect.addFlashAttribute("message", "refund accepted: " + refundReference);
    } catch (GatewayException refused) {
      return failed(redirect, target, "refund refused: " + refused.describe());
    }
    return "redirect:" + target;
  }

  private static String failed(RedirectAttributes redirect, String error) {
    return failed(redirect, "/payments", error);
  }

  private static String failed(RedirectAttributes redirect, String target, String error) {
    redirect.addFlashAttribute("error", error);
    return "redirect:" + target;
  }

  /** One order line as the breakdown shows it, with whether it can still be refunded. */
  public record OrderLineRow(
      String orderLineReference,
      String merchantLineReference,
      String productType,
      String net,
      String tax,
      String taxRate,
      boolean refunded) {
    static OrderLineRow of(OrderLine line) {
      return new OrderLineRow(
          line.orderLineReference(),
          line.merchantLineReference(),
          line.productType(),
          Money.format(line.netAmount()),
          Money.format(line.taxAmount()),
          line.taxRate().toPlainString(),
          line.refunded());
    }
  }

  /** One booked event as the breakdown shows it. */
  public record EventRow(
      String id,
      String transactionType,
      String transactionReference,
      String amount,
      String currency,
      String eventType,
      String occurredAt) {
    static EventRow of(PaymentEvent event) {
      return new EventRow(
          Long.toString(event.transactionEventId()),
          event.transactionType(),
          event.transactionReference(),
          Money.format(event.quantity()),
          event.currency(),
          event.eventType(),
          event.occurredAt().toString());
    }
  }

  /** One journal line as the breakdown shows it, with the entry it belongs to. */
  public record LineRow(
      String entryId,
      String entryType,
      String eventType,
      String transactionReference,
      String postedAt,
      String accountCode,
      String accountName,
      String registerType,
      String currency,
      String debit,
      String credit) {
    static LineRow of(PaymentJournalLine line) {
      return new LineRow(
          Long.toString(line.journalEntryId()),
          line.entryType(),
          line.eventType(),
          line.transactionReference(),
          line.postedAt().toString(),
          line.accountCode(),
          line.accountName(),
          line.registerType(),
          line.currency(),
          line.quantity() > 0 ? Money.format(line.quantity()) : "",
          line.quantity() < 0 ? Money.format(-line.quantity()) : "");
    }
  }

  /** The debits and credits one payment posted to one register in one currency. */
  record RegisterMovement(
      String accountCode,
      String accountName,
      String registerType,
      String currency,
      long debits,
      long credits) {
    RegisterMovement plus(RegisterMovement other) {
      return new RegisterMovement(
          accountCode,
          accountName,
          registerType,
          currency,
          debits + other.debits(),
          credits + other.credits());
    }
  }

  /** One balance account the payment posted to, as the breakdown shows it. */
  public record RegisterRow(
      String accountCode,
      String accountName,
      String registerType,
      String currency,
      String debits,
      String credits,
      String net) {
    static RegisterRow of(RegisterMovement movement) {
      return new RegisterRow(
          movement.accountCode(),
          movement.accountName(),
          movement.registerType(),
          movement.currency(),
          Money.format(movement.debits()),
          Money.format(movement.credits()),
          Money.formatSide(movement.debits() - movement.credits()));
    }
  }

  /** One catalogue item as the item list shows it. */
  public record CatalogueOption(String sku, String label) {}

  /** One test card and the outcome it selects. */
  public record Card(String number, String outcome) {}

  /** One payment as the table shows it. */
  public record PaymentRow(
      String orderReference,
      String pspReference,
      String merchantName,
      String merchantCode,
      String pspName,
      String status,
      boolean refundable,
      String currency,
      String gross,
      String net,
      String tax,
      String platformFee,
      String shopperCountry,
      String goodsTypes,
      String createdAt) {
    static PaymentRow of(Payment payment) {
      return new PaymentRow(
          payment.orderReference(),
          payment.pspReference() == null ? "" : payment.pspReference(),
          payment.merchantName(),
          payment.merchantCode(),
          payment.pspName(),
          status(payment),
          payment.refundable(),
          payment.currency(),
          Money.format(payment.grossAmount()),
          Money.format(payment.netAmount()),
          Money.format(payment.taxAmount()),
          payment.platformFee() == null ? "" : Money.format(payment.platformFee()),
          payment.shopperCountry(),
          String.join(", ", payment.goodsTypes()),
          payment.createdAt().toString());
    }

    private static String status(Payment payment) {
      if (payment.pspReference() == null) {
        return "PSP error";
      }
      String event = payment.lastEvent();
      if (event == null) {
        return "created";
      }
      return switch (event) {
        case "ORDER_CREATED" -> "created";
        case "AUTHORISED" -> "authorised";
        case "REFUSED" -> "refused";
        case "CAPTURED" -> "captured";
        case "CAPTURE_FAILED" -> "capture failed";
        case "REFUNDED" -> payment.refundable() ? "partly refunded" : "refunded";
        default -> event.toLowerCase(Locale.ROOT).replace('_', ' ');
      };
    }
  }
}
