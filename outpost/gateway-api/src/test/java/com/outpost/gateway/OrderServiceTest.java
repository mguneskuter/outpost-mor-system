package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.common.iso.Currencies;
import com.outpost.gateway.order.client.LedgerClient;
import com.outpost.gateway.order.client.LedgerPayment;
import com.outpost.gateway.order.client.ledger.LedgerClientException;
import com.outpost.gateway.order.repository.OrderRepository;
import com.outpost.gateway.order.repository.OrderRepository.PersistedOrder;
import com.outpost.gateway.order.service.CreateOrderCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderLineCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.ShopperDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderResult;
import com.outpost.gateway.order.service.OrderCreationException;
import com.outpost.gateway.order.service.OrderPhases;
import com.outpost.gateway.order.service.OrderService;
import com.outpost.integration.psp.service.CancelRequest;
import com.outpost.integration.psp.service.CancelResult;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.RefundRequest;
import com.outpost.integration.psp.service.RefundResult;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.tax.TaxRate;
import com.outpost.tax.provider.TaxRateProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
  private static final long MERCHANT_ACCOUNT_ID = 10;
  private static final String PAYMENT_METHOD = "DEMO_PSP";

  @Test
  void createsOrderWithLineTaxAndDurableExternalPhases() {
    TestDependencies dependencies = new TestDependencies();

    CreateOrderResult result = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    assertThat(result.netAmount()).isEqualTo(100);
    assertThat(result.taxAmount()).isEqualTo(19);
    assertThat(result.grossAmount()).isEqualTo(119);
    assertThat(result.paymentLink()).isEqualTo("https://pay.example/order-1");
    assertThat(result.lines())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.netAmount()).isEqualTo(100);
              assertThat(line.taxAmount()).isEqualTo(19);
              assertThat(line.grossAmount()).isEqualTo(119);
              assertThat(line.taxRate()).isEqualTo("0.19");
            });
    assertThat(dependencies.ledgerPayments).hasSize(1);
    assertThat(dependencies.pspRequests).hasSize(1);
    PersistedOrder persisted = Objects.requireNonNull(dependencies.repository.persisted);
    assertThat(persisted.phase()).isEqualTo(OrderPhases.COMPLETED);
  }

  @Test
  void replaysExactRequestWithoutCallingExternalSystemsAgain() {
    TestDependencies dependencies = new TestDependencies();
    CreateOrderCommand command = validCommand();

    CreateOrderResult first = dependencies.service.create(MERCHANT_ACCOUNT_ID, command);
    CreateOrderResult replay = dependencies.service.create(MERCHANT_ACCOUNT_ID, command);

    assertThat(replay).isEqualTo(first);
    assertThat(dependencies.ledgerPayments).hasSize(1);
    assertThat(dependencies.pspRequests).hasSize(1);
  }

  @Test
  void rejectsChangedRequestForAnExistingIdempotencyKey() {
    TestDependencies dependencies = new TestDependencies();
    dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    CreateOrderCommand changed =
        new CreateOrderCommand(
            "different-reference",
            "same-key",
            validCommand().shopperDetails(),
            PAYMENT_METHOD,
            validCommand().orderDetails());

    assertThatThrownBy(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, changed))
        .isInstanceOf(OrderCreationException.class)
        .satisfies(
            error -> {
              OrderCreationException exception = (OrderCreationException) error;
              assertThat(exception.status()).isEqualTo(409);
              assertThat(exception.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
            });
  }

  @Test
  void resumesPspFailureWithThePersistedPaymentReference() {
    TestDependencies dependencies = new TestDependencies();
    dependencies.pspResults.add(
        new com.outpost.integration.psp.service.CreateOrderResult("", "", ResultCode.REJECTED));
    dependencies.pspResults.add(
        new com.outpost.integration.psp.service.CreateOrderResult(
            "psp-1", "https://pay.example/order-1", ResultCode.ACCEPTED));

    assertThatThrownBy(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand()))
        .isInstanceOf(OrderCreationException.class)
        .satisfies(
            error -> {
              OrderCreationException exception = (OrderCreationException) error;
              assertThat(exception.status()).isEqualTo(503);
              assertThat(exception.code()).isEqualTo("PSP_RETRYABLE");
            });

    String paymentReference = dependencies.pspRequests.get(0).paymentReference();
    CreateOrderResult result = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    assertThat(result.paymentLink()).isEqualTo("https://pay.example/order-1");
    assertThat(dependencies.ledgerPayments).hasSize(1);
    assertThat(dependencies.pspRequests).hasSize(2);
    assertThat(dependencies.pspRequests.get(1).paymentReference()).isEqualTo(paymentReference);
  }

  @Test
  void retriesLedgerWithTheOriginalShopperJurisdiction() {
    TestDependencies dependencies = new TestDependencies();
    dependencies.ledgerFailures.add(
        new LedgerClientException("temporary Ledger failure", true, new RuntimeException()));
    CreateOrderCommand command =
        withShopper(
            validCommand(),
            new ShopperDetailsCommand("Shopper", "shopper@example.com", "US", "US-CA", null));

    assertThatThrownBy(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, command))
        .isInstanceOf(OrderCreationException.class);

    dependencies.repository.changeCurrentShopperJurisdiction("DE", null);
    dependencies.service.create(MERCHANT_ACCOUNT_ID, command);

    assertThat(dependencies.ledgerPayments).hasSize(2);
    assertThat(dependencies.ledgerPayments.get(1).shopperCountry().getIsoCode()).isEqualTo("US");
    assertThat(
            Objects.requireNonNull(dependencies.ledgerPayments.get(1).shopperCountrySubdivision())
                .getCode())
        .isEqualTo("US-CA");
  }

  @Test
  void equalRequestsDoNotTakeActiveClaim() throws Exception {
    TestDependencies dependencies = new TestDependencies();
    CountDownLatch ledgerEntered = new CountDownLatch(1);
    CountDownLatch releaseLedger = new CountDownLatch(1);
    dependencies.ledgerGate =
        payment -> {
          dependencies.ledgerPayments.add(payment);
          ledgerEntered.countDown();
          await(releaseLedger);
        };

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      final var first =
          executor.submit(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand()));
      final var second =
          executor.submit(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand()));
      assertThat(ledgerEntered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(dependencies.repository.blockedClaimAttempt().await(5, TimeUnit.SECONDS)).isTrue();
      releaseLedger.countDown();

      assertThat(first.get()).isEqualTo(second.get());
    }

    assertThat(dependencies.ledgerPayments).hasSize(1);
    assertThat(dependencies.pspRequests).hasSize(1);
  }

  @Test
  void recoversWhenClaimantTerminatesAfterAcquiringPhase() {
    TestDependencies dependencies = new TestDependencies();
    dependencies.ledgerGate =
        payment -> {
          dependencies.ledgerPayments.add(payment);
          dependencies.repository.terminateClaimant();
          throw new ClaimantTerminated();
        };

    assertThatThrownBy(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand()))
        .isInstanceOf(ClaimantTerminated.class);

    dependencies.ledgerGate = dependencies.ledgerPayments::add;
    CreateOrderResult result = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    assertThat(result.paymentLink()).isEqualTo("https://pay.example/order-1");
    assertThat(dependencies.ledgerPayments).hasSize(2);
    assertThat(dependencies.ledgerPayments.get(1).paymentReference())
        .isEqualTo(dependencies.ledgerPayments.get(0).paymentReference());
    assertThat(dependencies.pspRequests).hasSize(1);
  }

  @Test
  void rejectsAnOrderWithoutLines() {
    assertFailure(
        withOrderDetails(validCommand(), new OrderDetailsCommand(List.of(), 0L, "EUR")),
        "ORDER_LINES_REQUIRED");
  }

  @Test
  void rejectsDuplicateLineReferences() {
    assertFailure(
        withOrderDetails(
            validCommand(),
            new OrderDetailsCommand(List.of(line("same", 50L), line("same", 50L)), 100L, "EUR")),
        "DUPLICATE_MERCHANT_LINE_REFERENCE");
  }

  @Test
  void rejectsBlankLineReferences() {
    assertFailure(
        withOrderDetails(
            validCommand(), new OrderDetailsCommand(List.of(line("", 100L)), 100L, "EUR")),
        "INVALID_MERCHANT_LINE_REFERENCE");
  }

  @Test
  void rejectsNonPositiveLineAmounts() {
    assertFailure(
        withOrderDetails(
            validCommand(), new OrderDetailsCommand(List.of(line("line-1", 0L)), 0L, "EUR")),
        "LINE_AMOUNT_MUST_BE_POSITIVE");
  }

  @Test
  void rejectsMixedCurrencies() {
    assertFailure(
        withOrderDetails(
            validCommand(),
            new OrderDetailsCommand(List.of(line("line-1", 100L, "USD")), 100L, "EUR")),
        "MIXED_CURRENCIES");
  }

  @Test
  void rejectsAnOrderTotalThatDiffersFromLineTotals() {
    assertFailure(
        withOrderDetails(
            validCommand(), new OrderDetailsCommand(List.of(line("line-1", 100L)), 101L, "EUR")),
        "TOTAL_AMOUNT_MISMATCH");
  }

  @Test
  void rejectsAmountsThatOverflowMinorUnits() {
    assertFailure(
        withOrderDetails(
            validCommand(),
            new OrderDetailsCommand(
                List.of(line("line-1", Long.MAX_VALUE)), Long.MAX_VALUE, "EUR")),
        "AMOUNT_OVERFLOW");
  }

  @Test
  void rejectsBlankShopperEmail() {
    assertFailure(
        withShopper(validCommand(), new ShopperDetailsCommand("Shopper", "", "DE", null, null)),
        "INVALID_SHOPPER_DETAILS_EMAIL");
  }

  @Test
  void rejectsUnknownShopperCountry() {
    assertFailure(
        withShopper(
            validCommand(),
            new ShopperDetailsCommand("Shopper", "shopper@example.com", "ZZ", null, null)),
        "INVALID_COUNTRY");
  }

  @Test
  void rejectsUnknownShopperSubdivision() {
    assertFailure(
        withShopper(
            validCommand(),
            new ShopperDetailsCommand("Shopper", "shopper@example.com", "DE", "ZZ", null)),
        "INVALID_STATE");
  }

  @Test
  void rejectsSubdivisionOwnedByAnotherCountry() {
    assertFailure(
        withShopper(
            validCommand(),
            new ShopperDetailsCommand("Shopper", "shopper@example.com", "DE", "US-CA", null)),
        "INVALID_STATE");
  }

  @Test
  void appliesHalfEvenRoundingToTaxTies() {
    TestDependencies dependencies = new TestDependencies(rate("0.25"));

    CreateOrderResult result =
        dependencies.service.create(
            MERCHANT_ACCOUNT_ID,
            withOrderDetails(
                validCommand(), new OrderDetailsCommand(List.of(line("line-1", 6L)), 6L, "EUR")));

    assertThat(result.taxAmount()).isEqualTo(2L);
    assertThat(result.lines())
        .singleElement()
        .extracting(CreateOrderResult.OrderLineResult::taxAmount)
        .isEqualTo(2L);
  }

  @Test
  void sumsTaxAfterRoundingEachLine() {
    TestDependencies dependencies = new TestDependencies(rate("0.5"));

    CreateOrderResult result =
        dependencies.service.create(
            MERCHANT_ACCOUNT_ID,
            withOrderDetails(
                validCommand(),
                new OrderDetailsCommand(
                    List.of(line("line-1", 1L), line("line-2", 1L)), 2L, "EUR")));

    assertThat(result.taxAmount()).isZero();
    assertThat(result.lines())
        .extracting(CreateOrderResult.OrderLineResult::taxAmount)
        .containsExactly(0L, 0L);
  }

  @Test
  void rejectsAnUnavailablePsp() {
    TestDependencies unavailablePsp = new TestDependencies();
    unavailablePsp.repository.psp = Optional.empty();
    assertFailure(unavailablePsp, validCommand(), "PAYMENT_METHOD_UNAVAILABLE");
  }

  @Test
  void rejectsMissingFeeConfiguration() {

    TestDependencies missingFee = new TestDependencies();
    missingFee.repository.hasFee = false;
    assertFailure(missingFee, validCommand(), "MISSING_FEE_CONFIGURATION");
  }

  private static void assertFailure(CreateOrderCommand command, String code) {
    assertFailure(new TestDependencies(), command, code);
  }

  private static void assertFailure(
      TestDependencies dependencies, CreateOrderCommand command, String code) {
    assertThatThrownBy(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, command))
        .isInstanceOf(OrderCreationException.class)
        .satisfies(error -> assertThat(((OrderCreationException) error).code()).isEqualTo(code));
  }

  private static CreateOrderCommand validCommand() {
    return new CreateOrderCommand(
        "merchant-order-1",
        "same-key",
        new ShopperDetailsCommand("Shopper", "shopper@example.com", "DE", null, "10115"),
        PAYMENT_METHOD,
        new OrderDetailsCommand(List.of(line("line-1", 100L)), 100L, "EUR"));
  }

  private static OrderLineCommand line(String reference, long amount) {
    return line(reference, amount, "EUR");
  }

  private static OrderLineCommand line(String reference, long amount, String currency) {
    return new OrderLineCommand(reference, amount, currency, "DIGITAL_GOODS");
  }

  private static TaxRateProvider rate(String value) {
    BigDecimal rate = new BigDecimal(value);
    return (country, subdivision, productType, asOf) -> new TaxRate(country, subdivision, rate);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("timed out waiting for Ledger release");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted while waiting for Ledger release", exception);
    }
  }

  private static CreateOrderCommand withOrderDetails(
      CreateOrderCommand command, OrderDetailsCommand details) {
    return new CreateOrderCommand(
        command.merchantReference(),
        command.idempotencyKey(),
        command.shopperDetails(),
        command.paymentMethod(),
        details);
  }

  private static CreateOrderCommand withShopper(
      CreateOrderCommand command, ShopperDetailsCommand shopper) {
    return new CreateOrderCommand(
        command.merchantReference(),
        command.idempotencyKey(),
        shopper,
        command.paymentMethod(),
        command.orderDetails());
  }

  private static final class TestDependencies {
    private final FakeOrderRepository repository = new FakeOrderRepository();
    private final List<LedgerPayment> ledgerPayments =
        Collections.synchronizedList(new ArrayList<>());
    private final List<CreateOrderRequest> pspRequests =
        Collections.synchronizedList(new ArrayList<>());
    private final List<com.outpost.integration.psp.service.CreateOrderResult> pspResults =
        new ArrayList<>();
    private final List<LedgerClientException> ledgerFailures = new ArrayList<>();
    private LedgerClient ledgerGate = ledgerPayments::add;
    private final OrderService service;

    private TestDependencies() {
      this(taxRateProvider());
    }

    private TestDependencies(TaxRateProvider taxRates) {
      service =
          new OrderService(
              repository,
              payment -> {
                ledgerGate.createPayment(payment);
                if (!ledgerFailures.isEmpty()) {
                  throw ledgerFailures.remove(0);
                }
              },
              new FakePspClient(pspRequests, pspResults),
              taxRates,
              Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC));
    }
  }

  private static TaxRateProvider taxRateProvider() {
    return (country, subdivision, productType, asOf) ->
        new TaxRate(country, subdivision, new BigDecimal("0.19"));
  }

  private static final class FakePspClient implements PspClient {
    private final List<CreateOrderRequest> requests;
    private final List<com.outpost.integration.psp.service.CreateOrderResult> results;

    private FakePspClient(
        List<CreateOrderRequest> requests,
        List<com.outpost.integration.psp.service.CreateOrderResult> results) {
      this.requests = requests;
      this.results = results;
    }

    @Override
    public com.outpost.integration.psp.service.CreateOrderResult createOrder(
        CreateOrderRequest request) {
      requests.add(request);
      return results.isEmpty()
          ? new com.outpost.integration.psp.service.CreateOrderResult(
              "psp-1", "https://pay.example/order-1", ResultCode.ACCEPTED)
          : results.remove(0);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CancelResult cancel(CancelRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class FakeOrderRepository implements OrderRepository {
    private Optional<Psp> psp = Optional.of(new Psp(20, PAYMENT_METHOD));
    private boolean hasFee = true;
    private @Nullable PersistedOrder persisted;
    private String currentShopperCountry = "DE";
    private @Nullable String currentShopperSubdivision;
    private @Nullable UUID phaseClaim;
    private boolean claimantAlive;
    private final CountDownLatch blockedClaimAttempt = new CountDownLatch(1);

    @Override
    public Optional<Merchant> findMerchant(long accountId) {
      return Optional.of(new Merchant(accountId, "MERCHANT"));
    }

    @Override
    public Optional<Psp> findEnabledPsp(long merchantAccountId, String pspCode) {
      return psp.filter(value -> value.code().equals(pspCode));
    }

    @Override
    public boolean hasFeeConfiguration(long merchantAccountId, long currencyId) {
      return hasFee;
    }

    @Override
    public synchronized @Nullable PersistedOrder findByIdempotency(
        long merchantAccountId, String idempotencyKey) {
      if (persisted == null
          || persisted.merchantAccountId() != merchantAccountId
          || !persisted.idempotencyKey().equals(idempotencyKey)) {
        return null;
      }
      return withCurrentShopper(persisted);
    }

    @Override
    public synchronized @Nullable PersistedOrder findByReference(
        long merchantAccountId, String orderReference) {
      if (persisted == null
          || persisted.merchantAccountId() != merchantAccountId
          || !persisted.orderReference().equals(orderReference)) {
        return null;
      }
      return withCurrentShopper(persisted);
    }

    @Override
    public synchronized @Nullable PersistedOrder insert(NewOrder order) {
      if (persisted != null) {
        return null;
      }
      currentShopperCountry = "DE";
      currentShopperSubdivision = order.shopper().subdivisionId() == null ? null : "US-CA";
      persisted =
          new PersistedOrder(
              1,
              order.orderReference(),
              order.merchantReference(),
              order.merchantAccountId(),
              "MERCHANT",
              100,
              currentShopperCountry,
              currentShopperSubdivision,
              order.shopper().countryId() == 29 ? "US" : "DE",
              order.shopper().subdivisionId() == null ? null : "US-CA",
              order.currencyId(),
              Currencies.EUR.getValue().getCurrencyCode(),
              order.netAmount(),
              order.taxAmount(),
              order.grossAmount(),
              order.idempotencyKey(),
              order.requestFingerprint(),
              order.paymentReference(),
              order.pspAccountId(),
              PAYMENT_METHOD,
              null,
              null,
              order.createdAt(),
              OrderPhases.ORDER_PERSISTED,
              order.lines());
      return persisted;
    }

    @Override
    public synchronized boolean claimPhase(long orderId, OrderPhases phase, UUID claimToken) {
      if (persisted == null || persisted.phase() != phase) {
        return false;
      }
      if (phaseClaim != null && claimantAlive) {
        blockedClaimAttempt.countDown();
        return false;
      }
      phaseClaim = claimToken;
      claimantAlive = true;
      return true;
    }

    private synchronized void terminateClaimant() {
      claimantAlive = false;
    }

    private CountDownLatch blockedClaimAttempt() {
      return blockedClaimAttempt;
    }

    @Override
    public synchronized void releasePhaseClaim(long orderId, OrderPhases phase, UUID claimToken) {
      if (phaseClaim != null && phaseClaim.equals(claimToken)) {
        phaseClaim = null;
        claimantAlive = false;
      }
    }

    @Override
    public synchronized void markLedgerCreated(long orderId, UUID claimToken) {
      assertThat(phaseClaim).isEqualTo(claimToken);
      persisted = withPhase(OrderPhases.LEDGER_CREATED, null, null);
      phaseClaim = null;
      claimantAlive = false;
    }

    @Override
    public synchronized void markPspCreated(
        long orderId, UUID claimToken, String pspReference, String paymentLink) {
      assertThat(phaseClaim).isEqualTo(claimToken);
      persisted = withPhase(OrderPhases.PSP_CREATED, pspReference, paymentLink);
      phaseClaim = null;
      claimantAlive = false;
    }

    @Override
    public synchronized void markCompleted(long orderId, UUID claimToken) {
      assertThat(phaseClaim).isEqualTo(claimToken);
      PersistedOrder current = Objects.requireNonNull(persisted);
      persisted = withPhase(OrderPhases.COMPLETED, current.pspReference(), current.paymentLink());
      phaseClaim = null;
      claimantAlive = false;
    }

    private synchronized PersistedOrder withPhase(
        OrderPhases phase, @Nullable String pspReference, @Nullable String paymentLink) {
      PersistedOrder current = Objects.requireNonNull(persisted);
      return new PersistedOrder(
          current.orderId(),
          current.orderReference(),
          current.merchantReference(),
          current.merchantAccountId(),
          current.merchantCode(),
          current.shopperId(),
          current.shopperCountry(),
          current.shopperCountrySubdivision(),
          current.paymentShopperCountry(),
          current.paymentShopperCountrySubdivision(),
          current.currencyId(),
          current.currency(),
          current.netAmount(),
          current.taxAmount(),
          current.grossAmount(),
          current.idempotencyKey(),
          current.requestFingerprint(),
          current.paymentReference(),
          current.pspAccountId(),
          current.pspCode(),
          pspReference,
          paymentLink,
          current.createdAt(),
          phase,
          current.lines());
    }

    private synchronized PersistedOrder withCurrentShopper(PersistedOrder current) {
      return new PersistedOrder(
          current.orderId(),
          current.orderReference(),
          current.merchantReference(),
          current.merchantAccountId(),
          current.merchantCode(),
          current.shopperId(),
          currentShopperCountry,
          currentShopperSubdivision,
          current.paymentShopperCountry(),
          current.paymentShopperCountrySubdivision(),
          current.currencyId(),
          current.currency(),
          current.netAmount(),
          current.taxAmount(),
          current.grossAmount(),
          current.idempotencyKey(),
          current.requestFingerprint(),
          current.paymentReference(),
          current.pspAccountId(),
          current.pspCode(),
          current.pspReference(),
          current.paymentLink(),
          current.createdAt(),
          current.phase(),
          current.lines());
    }

    private synchronized void changeCurrentShopperJurisdiction(
        String country, @Nullable String subdivision) {
      currentShopperCountry = country;
      currentShopperSubdivision = subdivision;
    }
  }

  private static final class ClaimantTerminated extends Error {}
}
