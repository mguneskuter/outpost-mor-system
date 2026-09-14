package com.outpost.gateway.order.service;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.framework.queue.QueueFullException;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.integration.psp.CreatePspOrderRequest;
import com.outpost.integration.psp.PspClient;
import com.outpost.integration.psp.PspResultCodes;
import com.outpost.integration.psp.UnknownPspResultException;
import com.outpost.payment.ShopperDetail;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/** Creates merchant orders and the payment that collects each of them. */
public final class OrderService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(OrderService.class));

  private final OrderRepository orders;
  private final AccountRepository accounts;
  private final MerchantPspRepository merchantPsps;
  private final MerchantFeeConfigurationRepository feeConfigurations;
  private final TimeOrderedQueue<AccountingQueueRequest> accountingQueue;
  private final PspClient pspClient;
  private final TaxRateProvider taxRates;

  /** Creates an order service from its persistence and external boundaries. */
  public OrderService(
      OrderRepository orders,
      AccountRepository accounts,
      MerchantPspRepository merchantPsps,
      MerchantFeeConfigurationRepository feeConfigurations,
      TimeOrderedQueue<AccountingQueueRequest> accountingQueue,
      PspClient pspClient,
      TaxRateProvider taxRates) {
    this.orders = orders;
    this.accounts = accounts;
    this.merchantPsps = merchantPsps;
    this.feeConfigurations = feeConfigurations;
    this.accountingQueue = accountingQueue;
    this.pspClient = pspClient;
    this.taxRates = taxRates;
  }

  /**
   * Creates an order for one authenticated merchant, or answers a repeat of an earlier request
   * under the same idempotency key.
   *
   * <p>The shopper, the order, and its lines are stored as one unit, which stores nothing when
   * another request has already used the idempotency key. The PSP is called afterwards, outside any
   * transaction; when it accepts, the order's creation is queued for the Ledger. When the
   * accounting queue holds its capacity, the order is still answered and its unqueued creation is
   * logged at error. A repeated request whose order has no PSP reference calls the PSP again with
   * the order's reference.
   *
   * @throws CreateOrderException for a request the platform cannot price or book, a different
   *     request under a used idempotency key, or a failed PSP call
   */
  public CreateOrderResult create(long merchantAccountId, CreateOrderCommand command) {
    String fingerprint = fingerprint(command);
    String idempotencyKey = command.idempotencyKey();
    Optional<Order> existing = orders.findOrderByIdempotencyKey(merchantAccountId, idempotencyKey);
    if (existing.isPresent()) {
      return answerRepeatedRequest(merchantAccountId, existing.orElseThrow(), command, fingerprint);
    }
    Checkout checkout = price(merchantAccountId, command);
    Optional<Order> created =
        orders.insertOrder(checkout.shopper(), unsavedOrder(idempotencyKey, fingerprint, checkout));
    created.ifPresent(order -> LOGGER.info("Order created", orderFields(order, checkout)));
    if (created.isEmpty()) {
      Order winner =
          orders
              .findOrderByIdempotencyKey(merchantAccountId, idempotencyKey)
              .orElseThrow(
                  () -> new IllegalStateException("Idempotency key is used by no stored order"));
      return answerRepeatedRequest(merchantAccountId, winner, command, fingerprint);
    }
    return requestPayment(merchantAccountId, created.orElseThrow(), checkout);
  }

  private CreateOrderResult answerRepeatedRequest(
      long merchantAccountId, Order order, CreateOrderCommand command, String fingerprint) {
    if (!order.getRequestFingerprint().equals(fingerprint)) {
      throw failure(CreateOrderErrorCodes.IDEMPOTENCY_CONFLICT);
    }
    LOGGER.info(
        "Order creation repeated",
        new StructuredLogField(LogFields.ORDER_REFERENCE, order.getOrderReference()));
    if (order.getPspReference().isPresent()) {
      return result(order);
    }
    return requestPayment(merchantAccountId, order, price(merchantAccountId, command));
  }

  private Order unsavedOrder(String idempotencyKey, String fingerprint, Checkout checkout) {
    ShopperDetail shopper = checkout.shopper();
    return new Order(
        null,
        "order-" + UUID.randomUUID(),
        checkout.merchantReference(),
        checkout.merchant(),
        null,
        shopper.getCountry(),
        shopper.getCountrySubdivision().orElse(null),
        checkout.netAmount(),
        checkout.taxAmount(),
        checkout.grossAmount(),
        idempotencyKey,
        fingerprint,
        checkout.pspAccount(),
        null,
        null,
        null,
        checkout.items());
  }

  private CreateOrderResult requestPayment(long merchantAccountId, Order order, Checkout checkout) {
    PspOrder pspOrder = createPspOrder(order, checkout.pspAccount().getCode());
    orders.updateOrderPspReferenceAndPaymentLink(
        order.getOrderReference(), pspOrder.pspReference(), pspOrder.paymentLink());
    LOGGER.info(
        "Payment created at the PSP",
        new StructuredLogField(LogFields.ORDER_REFERENCE, order.getOrderReference()),
        new StructuredLogField(LogFields.PSP_CODE, checkout.pspAccount().getCode()),
        new StructuredLogField(LogFields.PSP_REFERENCE, pspOrder.pspReference()));
    AccountingQueueRequest orderCreated = orderCreated(order, checkout, pspOrder);
    try {
      accountingQueue.add(orderCreated);
    } catch (QueueFullException full) {
      LOGGER.error(
          "Order creation not queued for the Ledger: the accounting queue is full",
          full,
          orderCreated.logFields());
    }
    return result(
        orders
            .findOrderByIdempotencyKey(merchantAccountId, order.getIdempotencyKey())
            .orElseThrow(() -> new IllegalStateException("Stored order disappeared")));
  }

  private static AccountingQueueRequest orderCreated(
      Order order, Checkout checkout, PspOrder pspOrder) {
    return new AccountingQueueRequest(
        AccountingQueueRequestTypes.ORDER_CREATED,
        order.getOrderReference(),
        order.getMerchantReference(),
        checkout.pspAccount().getCode(),
        pspOrder.pspReference(),
        null,
        null,
        checkout.merchant().getCode(),
        order.getShopperCountry(),
        order.getShopperCountrySubdivision().orElse(null),
        order.getNetAmount(),
        order.getTaxAmount(),
        order.getGrossAmount());
  }

  private PspOrder createPspOrder(Order order, String pspCode) {
    com.outpost.integration.psp.CreatePspOrderResult pspResult;
    try {
      pspResult =
          pspClient.createOrder(
              new CreatePspOrderRequest(
                  pspCode, order.getOrderReference(), order.getGrossAmount()));
    } catch (UnknownPspResultException exception) {
      LOGGER.warn(
          "PSP order creation failed",
          exception,
          new StructuredLogField(LogFields.ORDER_REFERENCE, order.getOrderReference()),
          new StructuredLogField(LogFields.PSP_CODE, pspCode));
      throw failure(CreateOrderErrorCodes.PSP_RETRYABLE);
    }
    String pspReference = pspResult.pspReference();
    String paymentLink = pspResult.paymentUrl();
    if (pspResult.resultCode() != PspResultCodes.ACCEPTED
        || pspReference == null
        || pspReference.isBlank()
        || paymentLink == null
        || paymentLink.isBlank()) {
      LOGGER.warn(
          "PSP did not accept the order",
          new StructuredLogField(LogFields.ORDER_REFERENCE, order.getOrderReference()),
          new StructuredLogField(LogFields.PSP_CODE, pspCode),
          new StructuredLogField(LogFields.PSP_RESULT, pspResult.resultCode().name()));
      throw failure(CreateOrderErrorCodes.PSP_RETRYABLE);
    }
    return new PspOrder(pspReference, paymentLink);
  }

  /**
   * Prices the order and resolves the accounts that serve it, refusing anything the platform cannot
   * book, before any row is stored or the PSP is called.
   */
  private Checkout price(long merchantAccountId, CreateOrderCommand command) {
    CreateOrderCommand.ShopperDetailsCommand shopper = command.shopperDetails();
    Country country =
        Countries.fromIsoCode(shopper.country())
            .orElseThrow(() -> failure(CreateOrderErrorCodes.INVALID_COUNTRY));
    CountrySubdivision subdivision = subdivision(country, shopper.state());
    CreateOrderCommand.OrderDetailsCommand details = command.orderDetails();
    Currency currency =
        Currencies.fromCurrencyCode(details.currency())
            .orElseThrow(() -> failure(CreateOrderErrorCodes.UNSUPPORTED_CURRENCY));
    Set<String> merchantLineReferences = new HashSet<>();
    List<OrderItem> items = new ArrayList<>();
    Amount netAmount = new Amount(currency, 0);
    Amount taxAmount = new Amount(currency, 0);
    for (CreateOrderCommand.OrderLineCommand input : details.orderLines()) {
      if (!merchantLineReferences.add(input.merchantLineReference())) {
        throw failure(CreateOrderErrorCodes.DUPLICATE_MERCHANT_LINE_REFERENCE);
      }
      if (!currency.getCurrencyCode().equals(input.currency())) {
        throw failure(CreateOrderErrorCodes.MIXED_CURRENCIES);
      }
      ProductType productType =
          ProductTypes.fromCode(input.type())
              .orElseThrow(() -> failure(CreateOrderErrorCodes.INVALID_PRODUCT_TYPE));
      TaxRate rate;
      try {
        rate = taxRates.getRate(country, subdivision, productType);
      } catch (RuntimeException exception) {
        throw failure(CreateOrderErrorCodes.TAX_RATE_UNAVAILABLE);
      }
      Amount lineNet = new Amount(currency, input.amount());
      Amount lineTax;
      try {
        lineTax = lineNet.multipliedBy(rate.rate());
        netAmount = netAmount.plus(lineNet);
        taxAmount = taxAmount.plus(lineTax);
      } catch (ArithmeticException exception) {
        throw failure(CreateOrderErrorCodes.AMOUNT_OVERFLOW);
      }
      items.add(
          new OrderItem(
              null,
              productType,
              "line-" + UUID.randomUUID(),
              input.merchantLineReference(),
              lineNet,
              lineTax,
              rate.rate()));
    }
    if (details.totalAmount() != netAmount.quantity()) {
      throw failure(CreateOrderErrorCodes.TOTAL_AMOUNT_MISMATCH);
    }
    Amount grossAmount;
    try {
      // Net and tax amounts are never negative, so no line's gross exceeds the order's gross.
      grossAmount = netAmount.plus(taxAmount);
    } catch (ArithmeticException exception) {
      throw failure(CreateOrderErrorCodes.AMOUNT_OVERFLOW);
    }
    Account merchant =
        accounts
            .findAccountById(merchantAccountId)
            .filter(account -> isActiveAccountOfType(account, AccountTypes.MERCHANT))
            .orElseThrow(() -> failure(CreateOrderErrorCodes.MERCHANT_NOT_FOUND));
    Account pspAccount =
        accounts
            .findAccountByCode(command.pspCode())
            .filter(account -> isActiveAccountOfType(account, AccountTypes.PSP))
            .filter(account -> merchantPsps.isPspEnabled(merchantAccountId, account.getAccountId()))
            .orElseThrow(() -> failure(CreateOrderErrorCodes.PSP_UNAVAILABLE));
    if (!feeConfigurations.hasFeeConfiguration(merchantAccountId, currency)) {
      throw failure(CreateOrderErrorCodes.MISSING_FEE_CONFIGURATION);
    }
    // The Ledger books the captured tax against the shopper country's tax authority; an order it
    // could never book is refused before the shopper pays.
    if (accounts.findTaxAuthorityAccountByCountryId(country.getCountryId()).isEmpty()) {
      throw failure(CreateOrderErrorCodes.MISSING_TAX_AUTHORITY);
    }
    return new Checkout(
        merchant,
        pspAccount,
        command.merchantReference(),
        new ShopperDetail(
            null,
            shopper.email(),
            shopper.fullName(),
            country,
            subdivision,
            nullableText(shopper.zipcode())),
        List.copyOf(items),
        netAmount,
        taxAmount,
        grossAmount);
  }

  private static boolean isActiveAccountOfType(Account account, AccountTypes type) {
    return account.isActive() && account.getAccountType().equals(type.getValue());
  }

  private static @Nullable CountrySubdivision subdivision(Country country, @Nullable String state) {
    if (state == null || state.isBlank()) {
      return null;
    }
    return CountrySubdivisions.fromCode(country, state)
        .orElseThrow(() -> failure(CreateOrderErrorCodes.INVALID_STATE));
  }

  private static String fingerprint(CreateOrderCommand command) {
    StringBuilder canonical = new StringBuilder();
    append(canonical, command.merchantReference());
    append(canonical, command.idempotencyKey());
    append(canonical, command.pspCode());
    CreateOrderCommand.ShopperDetailsCommand shopper = command.shopperDetails();
    append(canonical, shopper.fullName());
    append(canonical, shopper.email());
    append(canonical, shopper.country());
    append(canonical, shopper.state());
    append(canonical, shopper.zipcode());
    CreateOrderCommand.OrderDetailsCommand details = command.orderDetails();
    append(canonical, details.totalAmount());
    append(canonical, details.currency());
    append(canonical, details.orderLines().size());
    for (CreateOrderCommand.OrderLineCommand line : details.orderLines()) {
      append(canonical, line.merchantLineReference());
      append(canonical, line.amount());
      append(canonical, line.currency());
      append(canonical, line.type());
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

  private static @Nullable String nullableText(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static CreateOrderException failure(CreateOrderErrorCodes code) {
    return new CreateOrderException(code);
  }

  private static StructuredLogField[] orderFields(Order order, Checkout checkout) {
    return new StructuredLogField[] {
      new StructuredLogField(LogFields.ORDER_REFERENCE, order.getOrderReference()),
      new StructuredLogField(LogFields.MERCHANT_CODE, checkout.merchant().getCode()),
      new StructuredLogField(LogFields.PSP_CODE, checkout.pspAccount().getCode()),
      new StructuredLogField(LogFields.CURRENCY, order.getNetAmount().currency().getCurrencyCode()),
      new StructuredLogField(LogFields.NET_AMOUNT, Long.toString(order.getNetAmount().quantity())),
      new StructuredLogField(LogFields.TAX_AMOUNT, Long.toString(order.getTaxAmount().quantity())),
      new StructuredLogField(
          LogFields.GROSS_AMOUNT, Long.toString(order.getGrossAmount().quantity()))
    };
  }

  private static CreateOrderResult result(Order order) {
    return new CreateOrderResult(
        order.getOrderReference(),
        order
            .getCreatedAt()
            .orElseThrow(() -> new IllegalStateException("Order has no stored creation time")),
        order.getNetAmount().quantity(),
        order.getNetAmount().currency().getCurrencyCode(),
        order.getTaxAmount().quantity(),
        order.getGrossAmount().quantity(),
        order
            .getPaymentLink()
            .orElseThrow(() -> new IllegalStateException("Order has no payment link")),
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
      Account pspAccount,
      String merchantReference,
      ShopperDetail shopper,
      List<OrderItem> items,
      Amount netAmount,
      Amount taxAmount,
      Amount grossAmount) {}

  /** The order a PSP created for a payment reference. */
  private record PspOrder(String pspReference, String paymentLink) {}
}
