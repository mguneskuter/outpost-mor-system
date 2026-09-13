package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.framework.queue.QueuedItem;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.gateway.order.service.OrderService;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.CreateOrderResult;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** An {@link OrderService} over in-memory fakes of every boundary it depends on. */
final class OrderServiceFakes {
  static final long MERCHANT_ACCOUNT_ID = 10;
  static final String PSP_CODE = "DEMO_PSP";
  static final String PSP_REFERENCE = "psp-1";
  static final String PAYMENT_LINK = "https://pay.example/order-1";
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC);

  final FakeOrderRepository repository = new FakeOrderRepository();
  final FakeAccountRepository accounts = new FakeAccountRepository();
  final FakeMerchantPspRepository merchantPsps = new FakeMerchantPspRepository();
  final FakeMerchantFeeConfigurationRepository feeConfigurations =
      new FakeMerchantFeeConfigurationRepository();
  final TimeOrderedQueue<AccountingQueueRequest> accountingQueue;
  final List<CreateOrderRequest> pspRequests = new ArrayList<>();
  final List<CreateOrderResult> pspResults = new ArrayList<>();
  final FakePspClient psp = new FakePspClient(pspRequests, pspResults);
  final OrderService service;

  OrderServiceFakes() {
    this(rate("0.19"));
  }

  OrderServiceFakes(TaxRateProvider taxRates) {
    this(taxRates, 100);
  }

  OrderServiceFakes(TaxRateProvider taxRates, int accountingQueueCapacity) {
    accountingQueue = new TimeOrderedQueue<>(FIXED_CLOCK, accountingQueueCapacity);
    service =
        new OrderService(
            repository,
            accounts,
            merchantPsps,
            feeConfigurations,
            accountingQueue,
            psp,
            taxRates,
            new LineTaxCalculator());
  }

  /** Removes and returns every queued request, in queue order. */
  List<AccountingQueueRequest> queued() {
    List<AccountingQueueRequest> requests = new ArrayList<>();
    Optional<QueuedItem<AccountingQueueRequest>> item = accountingQueue.poll();
    while (item.isPresent()) {
      requests.add(item.orElseThrow().payload());
      item = accountingQueue.poll();
    }
    return requests;
  }

  static TaxRateProvider rate(String value) {
    BigDecimal rate = new BigDecimal(value);
    return (country, subdivision, productType) ->
        new TaxRate(country, subdivision, productType, rate);
  }

  static final class FakePspClient implements PspClient {
    private final List<CreateOrderRequest> requests;
    private final List<CreateOrderResult> results;

    private FakePspClient(List<CreateOrderRequest> requests, List<CreateOrderResult> results) {
      this.requests = requests;
      this.results = results;
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
      requests.add(request);
      return results.isEmpty()
          ? new CreateOrderResult(PSP_REFERENCE, PAYMENT_LINK, ResultCode.ACCEPTED)
          : results.remove(0);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  static final class FakeAccountRepository implements AccountRepository {
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
    private static final Account TAX_AUTHORITY =
        Account.of(
            30,
            AccountTypes.TAX_AUTHORITY.getValue(),
            "TAX_AUTHORITY_DE",
            "Tax authority",
            true,
            CREATED,
            ROOT);
    final Set<Long> taxAuthorityCountryIds =
        new HashSet<>(Set.of(Countries.GERMANY.getValue().getCountryId()));

    @Override
    public Optional<Account> findAccountById(long accountId) {
      return Optional.of(MERCHANT).filter(account -> account.getAccountId() == accountId);
    }

    @Override
    public Optional<Account> findAccountByCode(String code) {
      return Optional.of(PSP).filter(account -> account.getCode().equals(code));
    }

    @Override
    public Optional<Account> findTaxAuthorityAccountByCountryId(long countryId) {
      return Optional.of(TAX_AUTHORITY)
          .filter(ignored -> taxAuthorityCountryIds.contains(countryId));
    }
  }

  static final class FakeMerchantPspRepository implements MerchantPspRepository {
    boolean pspEnabled = true;

    @Override
    public boolean isPspEnabled(long merchantAccountId, long pspAccountId) {
      return pspEnabled;
    }

    @Override
    public List<Account> findEnabledPsps(long merchantAccountId) {
      throw new UnsupportedOperationException();
    }
  }

  static final class FakeMerchantFeeConfigurationRepository
      implements MerchantFeeConfigurationRepository {
    boolean hasFee = true;

    @Override
    public boolean hasFeeConfiguration(long merchantAccountId, Currency currency) {
      return hasFee;
    }
  }

  /** Stores orders by idempotency key; when {@link #failure} is set, every call throws it. */
  static final class FakeOrderRepository implements OrderRepository {
    private static final Instant STORED_AT = Instant.parse("2026-09-12T00:00:00Z");
    final Map<String, Order> orders = new HashMap<>();
    @Nullable RuntimeException failure;
    private long nextId = 1;

    Order onlyOrder() {
      assertThat(orders).hasSize(1);
      return orders.values().iterator().next();
    }

    @Override
    public Optional<Order> findOrderByIdempotencyKey(long accountId, String idempotencyKey) {
      failIfAsked();
      return Optional.ofNullable(orders.get(idempotencyKey))
          .filter(stored -> stored.getMerchantAccount().getAccountId() == accountId);
    }

    @Override
    public Optional<Order> findOrderByOrderReference(String orderReference) {
      failIfAsked();
      return orders.values().stream()
          .filter(stored -> stored.getOrderReference().equals(orderReference))
          .findFirst();
    }

    @Override
    public Optional<Order> insertOrder(ShopperDetail shopper, Order order) {
      failIfAsked();
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
              order.getMerchantAccount(),
              nextId++,
              order.getShopperCountry(),
              order.getShopperCountrySubdivision().orElse(null),
              order.getNetAmount(),
              order.getTaxAmount(),
              order.getGrossAmount(),
              order.getIdempotencyKey(),
              order.getRequestFingerprint(),
              order.getPspAccount(),
              null,
              null,
              STORED_AT,
              items);
      orders.put(order.getIdempotencyKey(), stored);
      return Optional.of(stored);
    }

    @Override
    public void updateOrderPspReferenceAndPaymentLink(
        String orderReference, String pspReference, String paymentLink) {
      failIfAsked();
      Order stored = findOrderByOrderReference(orderReference).orElseThrow();
      if (stored.getPspReference().isEmpty()) {
        orders.put(stored.getIdempotencyKey(), withPspFacts(stored, pspReference, paymentLink));
      }
    }

    private void failIfAsked() {
      if (failure != null) {
        throw failure;
      }
    }

    private static Order withPspFacts(Order order, String pspReference, String paymentLink) {
      return new Order(
          order.getOrderId().getAsLong(),
          order.getOrderReference(),
          order.getMerchantReference(),
          order.getMerchantAccount(),
          order.getShopperId().getAsLong(),
          order.getShopperCountry(),
          order.getShopperCountrySubdivision().orElse(null),
          order.getNetAmount(),
          order.getTaxAmount(),
          order.getGrossAmount(),
          order.getIdempotencyKey(),
          order.getRequestFingerprint(),
          order.getPspAccount(),
          pspReference,
          paymentLink,
          order.getCreatedAt().orElse(null),
          order.getItems());
    }
  }
}
