package com.outpost.gateway.order.service;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.gateway.order.client.LedgerClient;
import com.outpost.gateway.order.client.LedgerPayment;
import com.outpost.gateway.order.client.ledger.LedgerClientException;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.payment.ShopperDetail;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.payment.order.LineTaxCalculator;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.tax.TaxRate;
import com.outpost.tax.provider.TaxRateProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/** Creates merchant orders and the payment that collects each of them. */
public final class OrderService {
  private final OrderRepository orders;
  private final AccountRepository accounts;
  private final MerchantPspRepository merchantPsps;
  private final MerchantFeeConfigurationRepository feeConfigurations;
  private final LedgerClient ledger;
  private final PspClient psp;
  private final TaxRateProvider taxRates;
  private final LineTaxCalculator lineTaxCalculator;

  /** Creates an order service from its persistence and external boundaries. */
  public OrderService(
      OrderRepository orders,
      AccountRepository accounts,
      MerchantPspRepository merchantPsps,
      MerchantFeeConfigurationRepository feeConfigurations,
      LedgerClient ledger,
      PspClient psp,
      TaxRateProvider taxRates,
      LineTaxCalculator lineTaxCalculator) {
    this.orders = orders;
    this.accounts = accounts;
    this.merchantPsps = merchantPsps;
    this.feeConfigurations = feeConfigurations;
    this.ledger = ledger;
    this.psp = psp;
    this.taxRates = taxRates;
    this.lineTaxCalculator = lineTaxCalculator;
  }

  /**
   * Creates an order for one authenticated merchant, or answers a repeat of an earlier request
   * under the same idempotency key.
   *
   * <p>The shopper, the order, and its lines are stored as one unit, which stores nothing when
   * another request has already used the idempotency key. Ledger and the PSP are called afterwards,
   * outside any transaction. A repeated request whose order has no PSP reference calls them again
   * with the order's existing references.
   *
   * @throws OrderCreationException for an invalid request, a different request under a used
   *     idempotency key, or a failed Ledger or PSP call
   */
  public CreateOrderResult create(long merchantAccountId, CreateOrderCommand command) {
    String fingerprint = fingerprint(command);
    String idempotencyKey = required(command.idempotencyKey(), "idempotency_key");
    Optional<Order> existing = orders.findOrderByIdempotencyKey(merchantAccountId, idempotencyKey);
    if (existing.isPresent()) {
      return repeat(merchantAccountId, existing.orElseThrow(), command, fingerprint);
    }
    Checkout checkout = checkout(merchantAccountId, command);
    Optional<Order> created =
        orders.insertOrder(
            checkout.shopper(),
            unsavedOrder(merchantAccountId, idempotencyKey, fingerprint, checkout));
    if (created.isEmpty()) {
      Order winner =
          orders
              .findOrderByIdempotencyKey(merchantAccountId, idempotencyKey)
              .orElseThrow(
                  () -> new IllegalStateException("idempotency key is used by no stored order"));
      return repeat(merchantAccountId, winner, command, fingerprint);
    }
    return pay(merchantAccountId, created.orElseThrow(), checkout);
  }

  private CreateOrderResult repeat(
      long merchantAccountId, Order order, CreateOrderCommand command, String fingerprint) {
    if (!order.getRequestFingerprint().equals(fingerprint)) {
      throw failure(HttpStatus.CONFLICT.value(), "IDEMPOTENCY_CONFLICT");
    }
    if (order.getPspReference().isPresent()) {
      return result(order);
    }
    return pay(merchantAccountId, order, checkout(merchantAccountId, command));
  }

  private Order unsavedOrder(
      long merchantAccountId, String idempotencyKey, String fingerprint, Checkout checkout) {
    ShopperDetail shopper = checkout.shopper();
    return new Order(
        null,
        "order-" + UUID.randomUUID(),
        checkout.merchantReference(),
        merchantAccountId,
        null,
        shopper.getCountry(),
        shopper.getCountrySubdivision().orElse(null),
        checkout.netAmount(),
        checkout.taxAmount(),
        checkout.grossAmount(),
        idempotencyKey,
        fingerprint,
        "payment-" + UUID.randomUUID(),
        checkout.psp().getAccountId(),
        null,
        null,
        null,
        checkout.items());
  }

  private CreateOrderResult pay(long merchantAccountId, Order order, Checkout checkout) {
    createLedgerPayment(order, checkout.merchant().getCode(), checkout.psp().getCode());
    PspOrder pspOrder = createPspOrder(order, checkout.psp().getCode());
    orders.updateOrderPspReferenceAndPaymentLink(
        order.getPaymentReference(), pspOrder.pspReference(), pspOrder.paymentLink());
    return result(
        orders
            .findOrderByIdempotencyKey(merchantAccountId, order.getIdempotencyKey())
            .orElseThrow(() -> new IllegalStateException("stored order disappeared")));
  }

  private void createLedgerPayment(Order order, String merchantCode, String pspCode) {
    try {
      ledger.createPayment(
          new LedgerPayment(
              order.getPaymentReference(),
              merchantCode,
              pspCode,
              order.getShopperCountry(),
              order.getShopperCountrySubdivision().orElse(null),
              order.getNetAmount(),
              order.getTaxAmount(),
              order.getGrossAmount()));
    } catch (LedgerClientException exception) {
      if (exception.retryable()) {
        throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "LEDGER_RETRYABLE");
      }
      throw failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "LEDGER_REJECTED");
    } catch (RuntimeException exception) {
      throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "LEDGER_RETRYABLE");
    }
  }

  private PspOrder createPspOrder(Order order, String pspCode) {
    com.outpost.integration.psp.service.CreateOrderResult pspResult;
    try {
      pspResult =
          psp.createOrder(
              new CreateOrderRequest(pspCode, order.getPaymentReference(), order.getGrossAmount()));
    } catch (RuntimeException exception) {
      throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "PSP_RETRYABLE");
    }
    String pspReference = pspResult.pspReference();
    String paymentLink = pspResult.paymentUrl();
    if (pspResult.resultCode() != ResultCode.ACCEPTED
        || pspReference == null
        || pspReference.isBlank()
        || paymentLink == null
        || paymentLink.isBlank()) {
      throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "PSP_RETRYABLE");
    }
    return new PspOrder(pspReference, paymentLink);
  }

  private Checkout checkout(long merchantAccountId, CreateOrderCommand command) {
    final String merchantReference = required(command.merchantReference(), "merchant_reference");
    final String paymentMethod = required(command.paymentMethod(), "payment_method");
    CreateOrderCommand.ShopperDetailsCommand shopper =
        required(command.shopperDetails(), "shopper_details");
    final String email = required(shopper.email(), "shopper_details.email");
    final String fullName = required(shopper.fullName(), "shopper_details.full_name");
    Country country =
        Countries.fromIsoCode(required(shopper.country(), "shopper_details.country"))
            .orElseThrow(() -> failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "INVALID_COUNTRY"));
    CountrySubdivision subdivision = subdivision(country, shopper.state());
    CreateOrderCommand.OrderDetailsCommand details =
        required(command.orderDetails(), "order_details");
    List<CreateOrderCommand.OrderLineCommand> requestLines =
        details.orderLines() == null ? List.of() : nonNullLines(details.orderLines());
    if (requestLines.isEmpty()) {
      throw failure(HttpStatus.BAD_REQUEST.value(), "ORDER_LINES_REQUIRED");
    }
    Currency currency =
        Currencies.fromCurrencyCode(required(details.currency(), "order_details.currency"))
            .orElseThrow(() -> failure(HttpStatus.BAD_REQUEST.value(), "UNSUPPORTED_CURRENCY"));
    long requestedTotal = required(details.totalAmount(), "order_details.total_amount");
    Set<String> merchantLineReferences = new HashSet<>();
    List<OrderItem> items = new ArrayList<>();
    Amount netAmount = new Amount(currency, 0);
    Amount taxAmount = new Amount(currency, 0);
    for (CreateOrderCommand.OrderLineCommand input : requestLines) {
      String lineReference = required(input.merchantLineReference(), "merchant_line_reference");
      if (!merchantLineReferences.add(lineReference)) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "DUPLICATE_MERCHANT_LINE_REFERENCE");
      }
      long net = required(input.amount(), "order_lines.amount");
      if (net <= 0) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "LINE_AMOUNT_MUST_BE_POSITIVE");
      }
      if (!currency.getCurrencyCode().equals(required(input.currency(), "order_lines.currency"))) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "MIXED_CURRENCIES");
      }
      ProductType productType =
          ProductTypes.fromCode(required(input.type(), "order_lines.type"))
              .orElseThrow(() -> failure(HttpStatus.BAD_REQUEST.value(), "INVALID_PRODUCT_TYPE"));
      TaxRate rate;
      try {
        rate = taxRates.getRate(country, subdivision, productType);
      } catch (RuntimeException exception) {
        throw failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "TAX_RATE_UNAVAILABLE");
      }
      Amount lineNet = new Amount(currency, net);
      Amount lineTax;
      try {
        lineTax = lineTaxCalculator.calculateTax(lineNet, rate.rate());
        netAmount = netAmount.plus(lineNet);
        taxAmount = taxAmount.plus(lineTax);
      } catch (ArithmeticException exception) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "AMOUNT_OVERFLOW");
      }
      items.add(
          new OrderItem(
              null,
              productType,
              "line-" + UUID.randomUUID(),
              lineReference,
              lineNet,
              lineTax,
              rate.rate()));
    }
    if (requestedTotal != netAmount.quantity()) {
      throw failure(HttpStatus.BAD_REQUEST.value(), "TOTAL_AMOUNT_MISMATCH");
    }
    Amount grossAmount;
    try {
      // Net and tax amounts are never negative, so no line's gross exceeds the order's gross.
      grossAmount = netAmount.plus(taxAmount);
    } catch (ArithmeticException exception) {
      throw failure(HttpStatus.BAD_REQUEST.value(), "AMOUNT_OVERFLOW");
    }
    Account merchant =
        accounts
            .findAccountById(merchantAccountId)
            .filter(account -> isActive(account, AccountTypes.MERCHANT))
            .orElseThrow(() -> failure(HttpStatus.UNAUTHORIZED.value(), "MERCHANT_NOT_FOUND"));
    Account pspAccount =
        accounts
            .findAccountByCode(paymentMethod)
            .filter(account -> isActive(account, AccountTypes.PSP))
            .filter(account -> merchantPsps.isPspEnabled(merchantAccountId, account.getAccountId()))
            .orElseThrow(
                () ->
                    failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "PAYMENT_METHOD_UNAVAILABLE"));
    if (!feeConfigurations.hasFeeConfiguration(merchantAccountId, currency)) {
      throw failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "MISSING_FEE_CONFIGURATION");
    }
    return new Checkout(
        merchant,
        pspAccount,
        merchantReference,
        new ShopperDetail(
            null, email, fullName, country, subdivision, nullableText(shopper.zipcode())),
        List.copyOf(items),
        netAmount,
        taxAmount,
        grossAmount);
  }

  private static boolean isActive(Account account, AccountTypes type) {
    return account.isActive() && account.getAccountType().equals(type.getValue());
  }

  private static List<CreateOrderCommand.OrderLineCommand> nonNullLines(List<?> lines) {
    List<CreateOrderCommand.OrderLineCommand> result = new ArrayList<>();
    for (Object value : lines) {
      if (!(value instanceof CreateOrderCommand.OrderLineCommand line)) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "INVALID_ORDER_LINE");
      }
      result.add(line);
    }
    return List.copyOf(result);
  }

  private static @Nullable CountrySubdivision subdivision(Country country, @Nullable String state) {
    if (state == null || state.isBlank()) {
      return null;
    }
    return CountrySubdivisions.fromCode(country, state)
        .orElseThrow(() -> failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "INVALID_STATE"));
  }

  private static String fingerprint(CreateOrderCommand command) {
    StringBuilder canonical = new StringBuilder();
    append(canonical, command.merchantReference());
    append(canonical, command.idempotencyKey());
    append(canonical, command.paymentMethod());
    CreateOrderCommand.ShopperDetailsCommand shopper = command.shopperDetails();
    if (shopper == null) {
      append(canonical, null);
    } else {
      append(canonical, shopper.fullName());
      append(canonical, shopper.email());
      append(canonical, shopper.country());
      append(canonical, shopper.state());
      append(canonical, shopper.zipcode());
    }
    CreateOrderCommand.OrderDetailsCommand details = command.orderDetails();
    if (details == null) {
      append(canonical, null);
    } else {
      append(canonical, details.totalAmount());
      append(canonical, details.currency());
      if (details.orderLines() == null) {
        append(canonical, null);
      } else {
        append(canonical, details.orderLines().size());
        for (CreateOrderCommand.OrderLineCommand line : details.orderLines()) {
          if (line == null) {
            append(canonical, null);
          } else {
            append(canonical, line.merchantLineReference());
            append(canonical, line.amount());
            append(canonical, line.currency());
            append(canonical, line.type());
          }
        }
      }
    }
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void append(StringBuilder target, @Nullable Object value) {
    String text = value == null ? "<null>" : value.toString();
    target.append(text.length()).append(':').append(text).append('|');
  }

  private static String required(@Nullable String value, String field) {
    if (value == null || value.isBlank()) {
      throw failure(
          HttpStatus.BAD_REQUEST.value(),
          "INVALID_" + field.toUpperCase(Locale.ROOT).replace('.', '_'));
    }
    return value;
  }

  private static <T> T required(@Nullable T value, String field) {
    if (value == null) {
      throw failure(
          HttpStatus.BAD_REQUEST.value(),
          "INVALID_" + field.toUpperCase(Locale.ROOT).replace('.', '_'));
    }
    return value;
  }

  private static @Nullable String nullableText(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static OrderCreationException failure(int status, String code) {
    return new OrderCreationException(status, code);
  }

  private static CreateOrderResult result(Order order) {
    return new CreateOrderResult(
        order.getOrderReference(),
        order
            .getCreatedAt()
            .orElseThrow(() -> new IllegalStateException("order has no stored creation time")),
        order.getNetAmount().quantity(),
        order.getNetAmount().currency().getCurrencyCode(),
        order.getTaxAmount().quantity(),
        order.getGrossAmount().quantity(),
        order
            .getPaymentLink()
            .orElseThrow(() -> new IllegalStateException("order has no payment link")),
        order.getItems().stream()
            .map(
                item ->
                    new CreateOrderResult.OrderLineResult(
                        item.getOrderLineReference(),
                        item.getMerchantLineReference(),
                        item.getNetAmount().quantity(),
                        item.getTaxAmount().quantity(),
                        item.getNetAmount().plus(item.getTaxAmount()).quantity(),
                        item.getTaxRate().toPlainString()))
            .toList());
  }

  /** The validated, priced request, its shopper, and the accounts that serve it. */
  private record Checkout(
      Account merchant,
      Account psp,
      String merchantReference,
      ShopperDetail shopper,
      List<OrderItem> items,
      Amount netAmount,
      Amount taxAmount,
      Amount grossAmount) {}

  /** The order a PSP created for a payment reference. */
  private record PspOrder(String pspReference, String paymentLink) {}
}
