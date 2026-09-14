package com.outpost.gateway.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.framework.queue.QueuedItem;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.integration.psp.CreatePspOrderRequest;
import com.outpost.integration.psp.CreatePspOrderResult;
import com.outpost.integration.psp.PspClient;
import com.outpost.integration.psp.PspResultCodes;
import com.outpost.integration.psp.RefundPspOrderRequest;
import com.outpost.integration.psp.RefundPspOrderResult;
import com.outpost.payment.ShopperDetail;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.payment.refund.Refund;
import com.outpost.payment.refund.repository.RefundRepository;
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
public final class OrderServiceFakes {
  public static final long MERCHANT_ACCOUNT_ID = 10;
  public static final String PSP_CODE = "DEMO_PSP";
  public static final String PSP_REFERENCE = "psp-1";
  public static final String PAYMENT_LINK = "https://pay.example/order-1";
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC);

  public final FakeOrderRepository repository = new FakeOrderRepository();
  public final FakeAccountRepository accounts = new FakeAccountRepository();
  public final FakeMerchantPspRepository merchantPsps = new FakeMerchantPspRepository();
  public final FakeMerchantFeeConfigurationRepository feeConfigurations =
      new FakeMerchantFeeConfigurationRepository();
  public final TimeOrderedQueue<AccountingQueueRequest> accountingQueue;
  public final List<CreatePspOrderRequest> pspRequests = new ArrayList<>();
  public final List<CreatePspOrderResult> pspResults = new ArrayList<>();
  public final FakePspClient psp = new FakePspClient(pspRequests, pspResults);
  public final OrderService service;

  /** Creates fakes whose tax rate is 19%. */
  public OrderServiceFakes() {
    this(rate("0.19"));
  }

  /** Creates fakes over {@code taxRates}. */
  public OrderServiceFakes(TaxRateProvider taxRates) {
    this(taxRates, 100);
  }

  /** Creates fakes over {@code taxRates} with an accounting queue of this capacity. */
  public OrderServiceFakes(TaxRateProvider taxRates, int accountingQueueCapacity) {
    accountingQueue = new TimeOrderedQueue<>(FIXED_CLOCK, accountingQueueCapacity);
    service =
        new OrderService(
            repository, accounts, merchantPsps, feeConfigurations, accountingQueue, psp, taxRates);
  }

  /** Removes and returns every queued request, in queue order. */
  public List<AccountingQueueRequest> queued() {
    List<AccountingQueueRequest> requests = new ArrayList<>();
    Optional<QueuedItem<AccountingQueueRequest>> item = accountingQueue.poll();
    while (item.isPresent()) {
      requests.add(item.orElseThrow().payload());
      item = accountingQueue.poll();
    }
    return requests;
  }

  /** Returns a provider answering {@code value} for every jurisdiction and product type. */
  public static TaxRateProvider rate(String value) {
    BigDecimal rate = new BigDecimal(value);
    return (country, subdivision, productType) ->
        new TaxRate(country, subdivision, productType, rate);
  }

  /** A refund store no test scenario reaches; every call fails. */
  public static final RefundRepository UNREACHED_REFUNDS =
      new RefundRepository() {
        @Override
        public Optional<Refund> insertRefund(Refund refund) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Refund> findRefundByRefundReference(String refundReference) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<Refund> findRefundsByOriginalReference(String originalReference) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void updateRefundPspRefundReference(
            String refundReference, String pspRefundReference) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void updateRefundItemRefundFailed(String refundReference) {
          throw new UnsupportedOperationException();
        }
      };

  /** Accepts every order unless a result is queued; refunds are not supported. */
  public static final class FakePspClient implements PspClient {
    private final List<CreatePspOrderRequest> requests;
    private final List<CreatePspOrderResult> results;

    private FakePspClient(
        List<CreatePspOrderRequest> requests, List<CreatePspOrderResult> results) {
      this.requests = requests;
      this.results = results;
    }

    @Override
    public CreatePspOrderResult createOrder(CreatePspOrderRequest request) {
      requests.add(request);
      return results.isEmpty()
          ? new CreatePspOrderResult(PSP_REFERENCE, PAYMENT_LINK, PspResultCodes.ACCEPTED)
          : results.remove(0);
    }

    @Override
    public RefundPspOrderResult refund(RefundPspOrderRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  /** Holds one merchant, one PSP, and the German tax authority. */
  public static final class FakeAccountRepository implements AccountRepository {
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
    public final Set<Long> taxAuthorityCountryIds =
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

    @Override
    public Optional<Account> findAccountByAccountType(AccountType accountType) {
      throw new UnsupportedOperationException();
    }
  }

  /** Enables every PSP unless {@link #pspEnabled} is cleared. */
  public static final class FakeMerchantPspRepository implements MerchantPspRepository {
    public boolean pspEnabled = true;

    @Override
    public boolean isPspEnabled(long merchantAccountId, long pspAccountId) {
      return pspEnabled;
    }

    @Override
    public List<Account> findEnabledPsps(long merchantAccountId) {
      throw new UnsupportedOperationException();
    }
  }

  /** Has fee terms unless {@link #hasFee} is cleared. */
  public static final class FakeMerchantFeeConfigurationRepository
      implements MerchantFeeConfigurationRepository {
    public boolean hasFee = true;

    @Override
    public boolean hasFeeConfiguration(long merchantAccountId, Currency currency) {
      return hasFee;
    }

    @Override
    public Optional<MerchantFeeConfiguration> findMerchantFeeConfigurationByAccountAndCurrency(
        Account merchantAccount, Currency currency) {
      throw new UnsupportedOperationException();
    }
  }

  /** Stores orders by idempotency key; when {@link #failure} is set, every call throws it. */
  public static final class FakeOrderRepository implements OrderRepository {
    private static final Instant STORED_AT = Instant.parse("2026-09-12T00:00:00Z");
    public final Map<String, Order> orders = new HashMap<>();
    public @Nullable RuntimeException failure;
    private long nextId = 1;

    /** Returns the single stored order. */
    public Order onlyOrder() {
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
