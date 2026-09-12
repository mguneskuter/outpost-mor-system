package com.outpost.gateway.order.service;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.gateway.order.client.LedgerClient;
import com.outpost.gateway.order.client.LedgerPayment;
import com.outpost.gateway.order.client.ledger.LedgerClientException;
import com.outpost.gateway.order.repository.OrderRepository;
import com.outpost.gateway.order.repository.OrderRepository.Line;
import com.outpost.gateway.order.repository.OrderRepository.NewOrder;
import com.outpost.gateway.order.repository.OrderRepository.PersistedOrder;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.tax.TaxRate;
import com.outpost.tax.provider.TaxRateProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/** Coordinates tax calculation, durable order creation, Ledger, and PSP calls. */
public final class OrderService {
  private static final long PHASE_WAIT_NANOS = 65_000_000_000L;
  private final OrderRepository repository;
  private final LedgerClient ledger;
  private final PspClient psp;
  private final TaxRateProvider taxRates;
  private final Clock clock;

  /** Creates an order service from its persistence and external boundaries. */
  public OrderService(
      OrderRepository repository,
      LedgerClient ledger,
      PspClient psp,
      TaxRateProvider taxRates,
      Clock clock) {
    this.repository = repository;
    this.ledger = ledger;
    this.psp = psp;
    this.taxRates = taxRates;
    this.clock = clock;
  }

  /** Creates or resumes an order for one authenticated merchant. */
  public CreateOrderResult create(long merchantAccountId, CreateOrderCommand command) {
    String fingerprint = fingerprint(command);
    String idempotencyKey = required(command.idempotencyKey(), "idempotency_key");
    PersistedOrder existing = repository.findByIdempotency(merchantAccountId, idempotencyKey);
    if (existing != null) {
      if (!existing.requestFingerprint().equals(fingerprint)) {
        throw failure(HttpStatus.CONFLICT.value(), "IDEMPOTENCY_CONFLICT");
      }
      return resume(existing);
    }

    PreparedOrder prepared = prepare(merchantAccountId, command, fingerprint);
    PersistedOrder persisted = repository.insert(prepared.order());
    if (persisted == null) {
      PersistedOrder concurrent = repository.findByIdempotency(merchantAccountId, idempotencyKey);
      if (concurrent == null || !concurrent.requestFingerprint().equals(fingerprint)) {
        throw failure(HttpStatus.CONFLICT.value(), "IDEMPOTENCY_CONFLICT");
      }
      return resume(concurrent);
    }
    return resume(persisted);
  }

  private CreateOrderResult resume(PersistedOrder order) {
    PersistedOrder current = order;
    if (current.phase() == OrderPhases.ORDER_PERSISTED) {
      UUID claimToken = UUID.randomUUID();
      if (!claimOrWait(current, OrderPhases.ORDER_PERSISTED, claimToken)) {
        return resume(reload(current));
      }
      try {
        ledger.createPayment(toLedgerPayment(current));
      } catch (LedgerClientException exception) {
        repository.releasePhaseClaim(current.orderId(), OrderPhases.ORDER_PERSISTED, claimToken);
        if (exception.retryable()) {
          throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "LEDGER_RETRYABLE");
        }
        throw failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "LEDGER_REJECTED");
      } catch (RuntimeException exception) {
        repository.releasePhaseClaim(current.orderId(), OrderPhases.ORDER_PERSISTED, claimToken);
        throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "LEDGER_RETRYABLE");
      }
      repository.markLedgerCreated(current.orderId(), claimToken);
      current = reload(current);
    }
    if (current.phase() == OrderPhases.LEDGER_CREATED) {
      UUID claimToken = UUID.randomUUID();
      if (!claimOrWait(current, OrderPhases.LEDGER_CREATED, claimToken)) {
        return resume(reload(current));
      }
      com.outpost.integration.psp.service.CreateOrderResult pspResult;
      try {
        pspResult =
            psp.createOrder(
                new CreateOrderRequest(
                    current.pspCode(),
                    current.paymentReference(),
                    new Amount(currency(current), current.grossAmount())));
      } catch (RuntimeException exception) {
        repository.releasePhaseClaim(current.orderId(), OrderPhases.LEDGER_CREATED, claimToken);
        throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "PSP_RETRYABLE");
      }
      if (pspResult.resultCode() != ResultCode.ACCEPTED
          || pspResult.pspReference() == null
          || pspResult.pspReference().isBlank()
          || pspResult.paymentUrl() == null
          || pspResult.paymentUrl().isBlank()) {
        repository.releasePhaseClaim(current.orderId(), OrderPhases.LEDGER_CREATED, claimToken);
        throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "PSP_RETRYABLE");
      }
      repository.markPspCreated(
          current.orderId(), claimToken, pspResult.pspReference(), pspResult.paymentUrl());
      current = reload(current);
    }
    if (current.phase() == OrderPhases.PSP_CREATED) {
      UUID claimToken = UUID.randomUUID();
      if (!claimOrWait(current, OrderPhases.PSP_CREATED, claimToken)) {
        return resume(reload(current));
      }
      repository.markCompleted(current.orderId(), claimToken);
      current = reload(current);
    }
    if (current.phase() != OrderPhases.COMPLETED || current.paymentLink() == null) {
      throw new IllegalStateException("order has no completed payment link: " + current.orderId());
    }
    return result(current);
  }

  private boolean claimOrWait(PersistedOrder order, OrderPhases phase, UUID claimToken) {
    if (repository.claimPhase(order.orderId(), phase, claimToken)) {
      return true;
    }
    long deadline = System.nanoTime() + PHASE_WAIT_NANOS;
    while (System.nanoTime() < deadline) {
      PersistedOrder current = reload(order);
      if (current.phase() != phase) {
        return false;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "ORDER_RETRYABLE");
      }
      if (repository.claimPhase(order.orderId(), phase, claimToken)) {
        return true;
      }
    }
    throw failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "ORDER_RETRYABLE");
  }

  private PersistedOrder reload(PersistedOrder previous) {
    PersistedOrder current =
        repository.findByIdempotency(previous.merchantAccountId(), previous.idempotencyKey());
    if (current == null) {
      throw new IllegalStateException("order disappeared: " + previous.orderId());
    }
    return current;
  }

  private LedgerPayment toLedgerPayment(PersistedOrder order) {
    Country country =
        Countries.fromIsoCode(order.paymentShopperCountry())
            .orElseThrow(() -> new IllegalStateException("persisted country is not supported"));
    CountrySubdivision subdivision =
        order.paymentShopperCountrySubdivision() == null
            ? null
            : CountrySubdivisions.fromCode(country, order.paymentShopperCountrySubdivision())
                .orElseThrow(
                    () -> new IllegalStateException("persisted subdivision is not supported"));
    Currency currency = currency(order);
    return new LedgerPayment(
        order.paymentReference(),
        order.merchantCode(),
        order.pspCode(),
        country,
        subdivision,
        new Amount(currency, order.netAmount()),
        new Amount(currency, order.taxAmount()),
        new Amount(currency, order.grossAmount()));
  }

  private PreparedOrder prepare(
      long merchantAccountId, CreateOrderCommand command, String fingerprint) {
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
    Set<String> merchantReferences = new HashSet<>();
    List<Line> lines = new ArrayList<>();
    long netTotal = 0;
    long taxTotal = 0;
    LocalDate asOf = LocalDate.now(clock);
    for (int index = 0; index < requestLines.size(); index++) {
      CreateOrderCommand.OrderLineCommand input = requestLines.get(index);
      String lineReference = required(input.merchantLineReference(), "merchant_line_reference");
      if (!merchantReferences.add(lineReference)) {
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
        rate = taxRates.getRate(country, subdivision, productType, asOf);
      } catch (RuntimeException exception) {
        throw failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "TAX_RATE_UNAVAILABLE");
      }
      long tax;
      long gross;
      try {
        tax = BigDecimalSupport.roundedMinorUnits(net, rate.rate());
        gross = Math.addExact(net, tax);
        netTotal = Math.addExact(netTotal, net);
        taxTotal = Math.addExact(taxTotal, tax);
      } catch (ArithmeticException exception) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "AMOUNT_OVERFLOW");
      }
      lines.add(
          new Line(
              index + 1,
              productType.getProductTypeId(),
              "line-" + UUID.randomUUID(),
              lineReference,
              net,
              tax,
              rate.rate().toPlainString()));
      if (gross <= 0) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "AMOUNT_OVERFLOW");
      }
    }
    if (requestedTotal != netTotal) {
      throw failure(HttpStatus.BAD_REQUEST.value(), "TOTAL_AMOUNT_MISMATCH");
    }
    long grossTotal;
    try {
      grossTotal = Math.addExact(netTotal, taxTotal);
    } catch (ArithmeticException exception) {
      throw failure(HttpStatus.BAD_REQUEST.value(), "AMOUNT_OVERFLOW");
    }
    if (repository.findMerchant(merchantAccountId).isEmpty()) {
      throw failure(HttpStatus.UNAUTHORIZED.value(), "MERCHANT_NOT_FOUND");
    }
    OrderRepository.Psp pspAccount =
        repository
            .findEnabledPsp(merchantAccountId, paymentMethod)
            .orElseThrow(
                () ->
                    failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "PAYMENT_METHOD_UNAVAILABLE"));
    if (!repository.hasFeeConfiguration(merchantAccountId, currency.getCurrencyId())) {
      throw failure(HttpStatus.UNPROCESSABLE_ENTITY.value(), "MISSING_FEE_CONFIGURATION");
    }
    Instant createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    NewOrder order =
        new NewOrder(
            "order-" + UUID.randomUUID(),
            merchantReference,
            merchantAccountId,
            0,
            currency.getCurrencyId(),
            netTotal,
            taxTotal,
            grossTotal,
            required(command.idempotencyKey(), "idempotency_key"),
            fingerprint,
            "payment-" + UUID.randomUUID(),
            pspAccount.accountId(),
            createdAt,
            new OrderRepository.Shopper(
                email,
                fullName,
                country.getCountryId(),
                subdivision == null ? null : subdivision.getCountrySubdivisionId(),
                nullableText(shopper.zipcode())),
            lines);
    return new PreparedOrder(order);
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

  private CreateOrderResult result(PersistedOrder order) {
    return new CreateOrderResult(
        order.orderReference(),
        order.createdAt(),
        order.netAmount(),
        order.currency(),
        order.taxAmount(),
        order.grossAmount(),
        Objects.requireNonNull(order.paymentLink(), "paymentLink"),
        order.lines().stream()
            .map(
                line ->
                    new CreateOrderResult.OrderLineResult(
                        line.orderLineReference(),
                        line.merchantLineReference(),
                        line.netAmount(),
                        line.taxAmount(),
                        Math.addExact(line.netAmount(), line.taxAmount()),
                        line.taxRate()))
            .toList());
  }

  private static Currency currency(PersistedOrder order) {
    return Currencies.fromCurrencyCode(order.currency())
        .orElseThrow(() -> new IllegalStateException("persisted currency is not supported"));
  }

  private record PreparedOrder(NewOrder order) {}

  private static final class BigDecimalSupport {
    private BigDecimalSupport() {}

    static long roundedMinorUnits(long amount, java.math.BigDecimal rate) {
      return java.math.BigDecimal.valueOf(amount)
          .multiply(rate)
          .setScale(0, java.math.RoundingMode.HALF_EVEN)
          .longValueExact();
    }
  }
}
