package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.gateway.order.client.LedgerPayment;
import com.outpost.gateway.order.client.ledger.LedgerClientException;
import com.outpost.gateway.order.service.CreateOrderCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderLineCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.ShopperDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderResult;
import com.outpost.gateway.order.service.OrderCreationException;
import com.outpost.gateway.order.service.OrderService;
import com.outpost.integration.psp.service.CancelRequest;
import com.outpost.integration.psp.service.CancelResult;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.RefundRequest;
import com.outpost.integration.psp.service.RefundResult;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.payment.ShopperDetail;
import com.outpost.payment.order.LineTaxCalculator;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.tax.TaxRate;
import com.outpost.tax.provider.TaxRateProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
  private static final long MERCHANT_ACCOUNT_ID = 10;
  private static final String PSP_CODE = "DEMO_PSP";
  private static final String PSP_REFERENCE = "psp-1";
  private static final String PAYMENT_LINK = "https://pay.example/order-1";

  @Test
  void createsTheOrderCallsLedgerAndPspOnceAndReturnsTheStoredResponse() {
    TestDependencies dependencies = new TestDependencies();

    CreateOrderResult result = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    Order stored = dependencies.repository.onlyOrder();
    assertThat(dependencies.repository.orders).hasSize(1);
    assertThat(stored.getItems()).hasSize(1);
    assertThat(dependencies.ledgerPayments)
        .singleElement()
        .satisfies(
            payment -> {
              assertThat(payment.paymentReference()).isEqualTo(stored.getPaymentReference());
              assertThat(payment.merchantCode()).isEqualTo("MERCHANT");
              assertThat(payment.pspCode()).isEqualTo(PSP_CODE);
              assertThat(payment.grossAmount().quantity()).isEqualTo(119);
            });
    assertThat(dependencies.pspRequests)
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.paymentReference()).isEqualTo(stored.getPaymentReference());
              assertThat(request.amount().quantity()).isEqualTo(119);
            });
    assertThat(stored.getPspReference()).contains(PSP_REFERENCE);
    assertThat(stored.getPaymentLink()).contains(PAYMENT_LINK);
    assertThat(result.orderReference()).isEqualTo(stored.getOrderReference());
    assertThat(result.netAmount()).isEqualTo(100);
    assertThat(result.taxAmount()).isEqualTo(19);
    assertThat(result.grossAmount()).isEqualTo(119);
    assertThat(result.currency()).isEqualTo("EUR");
    assertThat(result.paymentLink()).isEqualTo(PAYMENT_LINK);
    assertThat(result.lines())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.merchantLineReference()).isEqualTo("line-1");
              assertThat(line.netAmount()).isEqualTo(100);
              assertThat(line.taxAmount()).isEqualTo(19);
              assertThat(line.grossAmount()).isEqualTo(119);
              assertThat(line.taxRate()).isEqualTo("0.19");
            });
  }

  @Test
  void ledgerFailureReturnsFailureAndLeavesOrderWithoutPspReference() {
    TestDependencies dependencies = new TestDependencies();
    dependencies.ledgerFailures.add(
        new LedgerClientException("Ledger unavailable", true, new RuntimeException()));

    assertFailure(dependencies, validCommand(), 503, "LEDGER_RETRYABLE");

    assertThat(dependencies.pspRequests).isEmpty();
    assertThat(dependencies.repository.onlyOrder().getPspReference()).isEmpty();
  }

  @Test
  void pspFailureReturnsFailureAndLeavesOrderWithoutPspReference() {
    TestDependencies dependencies = new TestDependencies();
    dependencies.pspResults.add(
        new com.outpost.integration.psp.service.CreateOrderResult("", "", ResultCode.REJECTED));

    assertFailure(dependencies, validCommand(), 503, "PSP_RETRYABLE");

    assertThat(dependencies.ledgerPayments).hasSize(1);
    assertThat(dependencies.repository.onlyOrder().getPspReference()).isEmpty();
  }

  @Test
  void repeatedRequestAfterSuccessReturnsStoredResponseWithoutCallingLedgerOrPsp() {
    TestDependencies dependencies = new TestDependencies();
    CreateOrderResult first = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    CreateOrderResult repeated = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    assertThat(repeated).isEqualTo(first);
    assertThat(dependencies.repository.orders).hasSize(1);
    assertThat(dependencies.ledgerPayments).hasSize(1);
    assertThat(dependencies.pspRequests).hasSize(1);
  }

  @Test
  void repeatedRequestAfterPspFailureCallsLedgerAndPspAgainWithSameReferences() {
    TestDependencies dependencies = new TestDependencies();
    dependencies.pspResults.add(
        new com.outpost.integration.psp.service.CreateOrderResult("", "", ResultCode.REJECTED));
    assertFailure(dependencies, validCommand(), 503, "PSP_RETRYABLE");

    CreateOrderResult result = dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    Order stored = dependencies.repository.onlyOrder();
    assertThat(dependencies.repository.orders).hasSize(1);
    assertThat(dependencies.ledgerPayments)
        .extracting(LedgerPayment::paymentReference)
        .containsExactly(stored.getPaymentReference(), stored.getPaymentReference());
    assertThat(dependencies.pspRequests)
        .extracting(CreateOrderRequest::paymentReference)
        .containsExactly(stored.getPaymentReference(), stored.getPaymentReference());
    assertThat(result.orderReference()).isEqualTo(stored.getOrderReference());
    assertThat(result.paymentLink()).isEqualTo(PAYMENT_LINK);
  }

  @Test
  void repeatedRequestSendsLedgerTheJurisdictionStoredOnTheOrder() {
    TestDependencies dependencies = new TestDependencies();
    dependencies.ledgerFailures.add(
        new LedgerClientException("Ledger unavailable", true, new RuntimeException()));
    assertFailure(dependencies, validCommand(), 503, "LEDGER_RETRYABLE");
    Country storedCountry = Countries.UNITED_STATES.getValue();
    CountrySubdivision storedSubdivision = CountrySubdivisions.US_CA.getValue();
    dependencies.repository.storeJurisdiction(storedCountry, storedSubdivision);

    dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());

    assertThat(dependencies.ledgerPayments).hasSize(2);
    LedgerPayment repeated = dependencies.ledgerPayments.get(1);
    assertThat(repeated.shopperCountry()).isEqualTo(storedCountry);
    assertThat(repeated.shopperCountrySubdivision()).isEqualTo(storedSubdivision);
  }

  @Test
  void differentRequestUnderUsedIdempotencyKeyIsConflict() {
    TestDependencies dependencies = new TestDependencies();
    dependencies.service.create(MERCHANT_ACCOUNT_ID, validCommand());
    CreateOrderCommand changed =
        new CreateOrderCommand(
            "different-reference",
            "same-key",
            validCommand().shopperDetails(),
            PSP_CODE,
            validCommand().orderDetails());

    assertFailure(dependencies, changed, 409, "IDEMPOTENCY_CONFLICT");

    assertThat(dependencies.repository.orders).hasSize(1);
    assertThat(dependencies.ledgerPayments).hasSize(1);
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
    unavailablePsp.merchantPsps.pspEnabled = false;
    assertFailure(unavailablePsp, validCommand(), 422, "PAYMENT_METHOD_UNAVAILABLE");
  }

  @Test
  void rejectsMissingFeeConfiguration() {
    TestDependencies missingFee = new TestDependencies();
    missingFee.feeConfigurations.hasFee = false;
    assertFailure(missingFee, validCommand(), 422, "MISSING_FEE_CONFIGURATION");
  }

  private static void assertFailure(CreateOrderCommand command, String code) {
    TestDependencies dependencies = new TestDependencies();
    assertThatThrownBy(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, command))
        .isInstanceOf(OrderCreationException.class)
        .satisfies(error -> assertThat(((OrderCreationException) error).code()).isEqualTo(code));
  }

  private static void assertFailure(
      TestDependencies dependencies, CreateOrderCommand command, int status, String code) {
    assertThatThrownBy(() -> dependencies.service.create(MERCHANT_ACCOUNT_ID, command))
        .isInstanceOf(OrderCreationException.class)
        .satisfies(
            error -> {
              OrderCreationException exception = (OrderCreationException) error;
              assertThat(exception.status()).isEqualTo(status);
              assertThat(exception.code()).isEqualTo(code);
            });
  }

  private static CreateOrderCommand validCommand() {
    return new CreateOrderCommand(
        "merchant-order-1",
        "same-key",
        new ShopperDetailsCommand("Shopper", "shopper@example.com", "DE", null, "10115"),
        PSP_CODE,
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
    return (country, subdivision, productType) ->
        new TaxRate(country, subdivision, productType, rate);
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
    private final FakeMerchantPspRepository merchantPsps = new FakeMerchantPspRepository();
    private final FakeMerchantFeeConfigurationRepository feeConfigurations =
        new FakeMerchantFeeConfigurationRepository();
    private final List<LedgerPayment> ledgerPayments = new ArrayList<>();
    private final List<LedgerClientException> ledgerFailures = new ArrayList<>();
    private final List<CreateOrderRequest> pspRequests = new ArrayList<>();
    private final List<com.outpost.integration.psp.service.CreateOrderResult> pspResults =
        new ArrayList<>();
    private final OrderService service;

    private TestDependencies() {
      this(rate("0.19"));
    }

    private TestDependencies(TaxRateProvider taxRates) {
      service =
          new OrderService(
              repository,
              new FakeAccountRepository(),
              merchantPsps,
              feeConfigurations,
              payment -> {
                ledgerPayments.add(payment);
                if (!ledgerFailures.isEmpty()) {
                  throw ledgerFailures.remove(0);
                }
              },
              new FakePspClient(pspRequests, pspResults),
              taxRates,
              new LineTaxCalculator());
    }
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
              PSP_REFERENCE, PAYMENT_LINK, ResultCode.ACCEPTED)
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

  private static final class FakeAccountRepository implements AccountRepository {
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Account ROOT =
        Account.of(1, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);
    private static final Account MERCHANT =
        Account.of(
            MERCHANT_ACCOUNT_ID,
            AccountTypes.MERCHANT.getValue(),
            "MERCHANT",
            "Merchant",
            true,
            CREATED,
            ROOT);
    private static final Account PSP =
        Account.of(20, AccountTypes.PSP.getValue(), PSP_CODE, "PSP", true, CREATED, ROOT);

    @Override
    public Optional<Account> findAccountById(long accountId) {
      return Optional.of(MERCHANT).filter(account -> account.getAccountId() == accountId);
    }

    @Override
    public Optional<Account> findAccountByCode(String code) {
      return Optional.of(PSP).filter(account -> account.getCode().equals(code));
    }
  }

  private static final class FakeMerchantPspRepository implements MerchantPspRepository {
    private boolean pspEnabled = true;

    @Override
    public boolean isPspEnabled(long merchantAccountId, long pspAccountId) {
      return pspEnabled;
    }
  }

  private static final class FakeMerchantFeeConfigurationRepository
      implements MerchantFeeConfigurationRepository {
    private boolean hasFee = true;

    @Override
    public boolean hasFeeConfiguration(
        long merchantAccountId, com.outpost.common.iso.Currencies.Currency currency) {
      return hasFee;
    }
  }

  private static final class FakeOrderRepository implements OrderRepository {
    private static final Instant STORED_AT = Instant.parse("2026-09-12T00:00:00Z");
    private final Map<String, Order> orders = new HashMap<>();
    private long nextId = 1;

    private Order onlyOrder() {
      assertThat(orders).hasSize(1);
      return orders.values().iterator().next();
    }

    private void storeJurisdiction(Country country, @Nullable CountrySubdivision subdivision) {
      Order stored = onlyOrder();
      orders.put(
          stored.getIdempotencyKey(),
          copy(
              stored,
              country,
              subdivision,
              stored.getPspReference().orElse(null),
              stored.getPaymentLink().orElse(null)));
    }

    @Override
    public Optional<Order> findOrderByIdempotencyKey(long accountId, String idempotencyKey) {
      return Optional.ofNullable(orders.get(idempotencyKey))
          .filter(stored -> stored.getAccountId() == accountId);
    }

    @Override
    public Optional<Order> findOrderByOrderReference(long accountId, String orderReference) {
      return orders.values().stream()
          .filter(stored -> stored.getAccountId() == accountId)
          .filter(stored -> stored.getOrderReference().equals(orderReference))
          .findFirst();
    }

    @Override
    public Optional<Order> findOrderByPaymentReference(String paymentReference) {
      return orders.values().stream()
          .filter(stored -> stored.getPaymentReference().equals(paymentReference))
          .findFirst();
    }

    @Override
    public Optional<PspRouting> findPspRoutingByPaymentReference(String paymentReference) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Order> insertOrder(ShopperDetail shopper, Order order) {
      if (orders.containsKey(order.getIdempotencyKey())) {
        return Optional.empty();
      }
      List<OrderItem> items =
          order.getItems().stream()
              .map(
                  item ->
                      new OrderItem(
                          nextId++,
                          item.getProductType(),
                          item.getOrderLineReference(),
                          item.getMerchantLineReference(),
                          item.getNetAmount(),
                          item.getTaxAmount(),
                          item.getTaxRate()))
              .toList();
      Order stored =
          new Order(
              nextId++,
              order.getOrderReference(),
              order.getMerchantReference(),
              order.getAccountId(),
              nextId++,
              order.getShopperCountry(),
              order.getShopperCountrySubdivision().orElse(null),
              order.getNetAmount(),
              order.getTaxAmount(),
              order.getGrossAmount(),
              order.getIdempotencyKey(),
              order.getRequestFingerprint(),
              order.getPaymentReference(),
              order.getPspAccountId(),
              null,
              null,
              STORED_AT,
              items);
      orders.put(order.getIdempotencyKey(), stored);
      return Optional.of(stored);
    }

    @Override
    public void updateOrderPspReferenceAndPaymentLink(
        String paymentReference, String pspReference, String paymentLink) {
      Order stored = findOrderByPaymentReference(paymentReference).orElseThrow();
      if (stored.getPspReference().isEmpty()) {
        orders.put(
            stored.getIdempotencyKey(),
            copy(
                stored,
                stored.getShopperCountry(),
                stored.getShopperCountrySubdivision().orElse(null),
                pspReference,
                paymentLink));
      }
    }

    private static Order copy(
        Order order,
        Country shopperCountry,
        @Nullable CountrySubdivision shopperCountrySubdivision,
        @Nullable String pspReference,
        @Nullable String paymentLink) {
      return new Order(
          order.getOrderId().getAsLong(),
          order.getOrderReference(),
          order.getMerchantReference(),
          order.getAccountId(),
          order.getShopperId().getAsLong(),
          shopperCountry,
          shopperCountrySubdivision,
          order.getNetAmount(),
          order.getTaxAmount(),
          order.getGrossAmount(),
          order.getIdempotencyKey(),
          order.getRequestFingerprint(),
          order.getPaymentReference(),
          order.getPspAccountId(),
          pspReference,
          paymentLink,
          order.getCreatedAt().orElse(null),
          order.getItems());
    }
  }
}
