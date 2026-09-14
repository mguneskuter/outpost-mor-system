package com.outpost.gateway.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.gateway.GatewayApiApplication;
import com.outpost.gateway.GatewayStaticDataFixtures;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderLineCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.ShopperDetailsCommand;
import com.outpost.integration.psp.CreatePspOrderRequest;
import com.outpost.integration.psp.PspClient;
import com.outpost.integration.psp.PspResultCodes;
import com.outpost.integration.psp.RefundPspOrderRequest;
import com.outpost.integration.psp.RefundPspOrderResult;
import com.outpost.integration.psp.UnknownPspResultException;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.tax.TaxRate;
import com.outpost.tax.provider.TaxRateProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = GatewayApiApplication.class)
class OrderServiceIntegrationTest {
  private static final String MERCHANT_CODE = "ORDER_MERCHANT";
  private static final String PSP_CODE = "ORDER_PSP";
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_order", "outpost_gateway_order", "outpost_gateway_order");
  private static long merchantAccountId;

  @Autowired private OrderRepository orders;
  @Autowired private AccountRepository accounts;
  @Autowired private MerchantPspRepository merchantPsps;
  @Autowired private MerchantFeeConfigurationRepository feeConfigurations;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void migrateAndSeed() {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    JdbcTemplate seed =
        new JdbcTemplate(
            DataSourceBuilder.create()
                .url(DATABASE.getJdbcUrl())
                .username(DATABASE.getUsername())
                .password(DATABASE.getPassword())
                .build());
    GatewayStaticDataFixtures.materializeAll(seed);
    long rootAccountId = account(seed, AccountTypes.ROOT, "ORDER_ROOT", null);
    merchantAccountId = account(seed, AccountTypes.MERCHANT, MERCHANT_CODE, rootAccountId);
    long pspAccountId = account(seed, AccountTypes.PSP, PSP_CODE, rootAccountId);
    long taxAuthorityAccountId =
        account(seed, AccountTypes.TAX_AUTHORITY, "TAX_AUTHORITY_DE", rootAccountId);
    seed.update(
        "INSERT INTO tax_authority_account (country_id, account_id, account_type_id) "
            + "VALUES (?, ?, ?)",
        Countries.GERMANY.getValue().getCountryId(),
        taxAuthorityAccountId,
        AccountTypes.TAX_AUTHORITY.getValue().getAccountTypeId());
    seed.update(
        "INSERT INTO merchant_psp (account_id, psp_account_id) VALUES (?, ?)",
        merchantAccountId,
        pspAccountId);
    seed.update(
        "INSERT INTO merchant_fee_configuration "
            + "(account_id, account_type_id, currency_id, fee_mode_id, fee_rate_bps) "
            + "VALUES (?, ?, ?, ?, 0)",
        merchantAccountId,
        AccountTypes.MERCHANT.getValue().getAccountTypeId(),
        Currencies.EUR.getValue().getCurrencyId(),
        FeeModes.PERCENTAGE.getValue().getFeeModeId());
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
    registry.add(
        "OUTPOST_HMAC_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    registry.add("OUTPOST_OPERATOR_API_KEY", () -> "integration-operator-key");
    registry.add("OUTPOST_LEDGER_GATEWAY_HMAC_SECRET", () -> "gateway-integration-test-key");
  }

  @Test
  void storesPaymentFactsAndTheShopperJurisdictionOnTheOrderRow() {
    OrderService service = service(rate(), new AcceptingPsp());

    CreateOrderResult result =
        service.create(merchantAccountId, command("stored-key", "stored-order", "stored"));

    Map<String, Object> order =
        jdbcTemplate.queryForMap(
            "SELECT psp_reference, payment_link, shopper_country_id, "
                + "shopper_country_subdivision_id FROM merchant_order WHERE order_reference = ?",
            result.orderReference());
    assertThat(order)
        .containsEntry("psp_reference", AcceptingPsp.PSP_REFERENCE)
        .containsEntry("payment_link", AcceptingPsp.PAYMENT_LINK)
        .containsEntry("shopper_country_id", Countries.GERMANY.getValue().getCountryId());
    assertThat(order.get("shopper_country_subdivision_id")).isNull();
    assertThat(itemCount(result.orderReference())).isEqualTo(1);
  }

  @Test
  void refusesOrderForCountryWithoutTaxAuthorityBeforeStoringItOrCallingThePsp() {
    RecordingPsp psp = new RecordingPsp();
    OrderService service = service(rate(), psp);
    CreateOrderCommand command =
        command(
            "untaxed-key", "untaxed-order", "untaxed", Countries.AUSTRIA.getValue().getIsoCode());

    CreateOrderException refusal =
        catchThrowableOfType(
            CreateOrderException.class, () -> service.create(merchantAccountId, command));
    assertThat(refusal.code()).isEqualTo(CreateOrderErrorCodes.MISSING_TAX_AUTHORITY);
    assertThat(orderCount("untaxed-key")).isZero();
    assertThat(psp.requests).isEmpty();
  }

  @Test
  void keepsTheStoredOrderWithoutPspFactsAndQueuesNothingWhenThePspAnswerIsLost() {
    TimeOrderedQueue<AccountingQueueRequest> accountingQueue =
        new TimeOrderedQueue<>(Clock.systemUTC(), 10);
    OrderService service = service(rate(), new UnreachablePsp(), accountingQueue);

    CreateOrderException failure =
        catchThrowableOfType(
            CreateOrderException.class,
            () ->
                service.create(merchantAccountId, command("lost-key", "lost-order", "lost", "DE")));
    assertThat(failure.code()).isEqualTo(CreateOrderErrorCodes.PSP_RETRYABLE);
    Map<String, Object> order =
        jdbcTemplate.queryForMap(
            "SELECT psp_reference, payment_link FROM merchant_order WHERE idempotency_key = ?",
            "lost-key");
    assertThat(order.get("psp_reference")).isNull();
    assertThat(order.get("payment_link")).isNull();
    assertThat(accountingQueue.size()).isZero();
  }

  @Test
  void concurrentRequestsWithOneKeyAndDifferentBodiesCommitOnlyTheWinner() throws Exception {
    CyclicBarrier bothPriced = new CyclicBarrier(2);
    OrderService service = service(barrierRate(bothPriced), new AcceptingPsp());
    CreateOrderCommand first = command("race-key", "race-order-first", "first");
    CreateOrderCommand second = command("race-key", "race-order-second", "second");

    List<ConcurrentAttempt> attempts = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<ConcurrentAttempt> firstAttempt = executor.submit(() -> attempt(service, first));
      Future<ConcurrentAttempt> secondAttempt = executor.submit(() -> attempt(service, second));
      attempts.add(firstAttempt.get(30, TimeUnit.SECONDS));
      attempts.add(secondAttempt.get(30, TimeUnit.SECONDS));
    }

    ConcurrentAttempt winner =
        attempts.stream()
            .filter(concurrentAttempt -> concurrentAttempt.result() != null)
            .findFirst()
            .orElseThrow();
    assertThat(attempts)
        .filteredOn(concurrentAttempt -> concurrentAttempt.conflicted())
        .as("exactly one request loses with a conflict")
        .hasSize(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM merchant_order WHERE account_id = ? AND idempotency_key = ?",
                Integer.class,
                merchantAccountId,
                "race-key"))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT full_name FROM shopper_detail WHERE email IN (?, ?)",
                String.class,
                shopperEmail("first"),
                shopperEmail("second")))
        .containsExactly(winner.shopperName());
  }

  private OrderService service(TaxRateProvider taxRates, PspClient psp) {
    return service(taxRates, psp, new TimeOrderedQueue<>(Clock.systemUTC(), 10));
  }

  private OrderService service(
      TaxRateProvider taxRates,
      PspClient psp,
      TimeOrderedQueue<AccountingQueueRequest> accountingQueue) {
    return new OrderService(
        orders, accounts, merchantPsps, feeConfigurations, accountingQueue, psp, taxRates);
  }

  private static ConcurrentAttempt attempt(OrderService service, CreateOrderCommand command) {
    String shopperName = command.shopperDetails().fullName();
    try {
      return new ConcurrentAttempt(shopperName, service.create(merchantAccountId, command), false);
    } catch (CreateOrderException exception) {
      return new ConcurrentAttempt(
          shopperName, null, exception.code() == CreateOrderErrorCodes.IDEMPOTENCY_CONFLICT);
    }
  }

  private static CreateOrderCommand command(
      String idempotencyKey, String merchantReference, String shopperName) {
    return command(idempotencyKey, merchantReference, shopperName, "DE");
  }

  private static CreateOrderCommand command(
      String idempotencyKey, String merchantReference, String shopperName, String countryCode) {
    return new CreateOrderCommand(
        merchantReference,
        idempotencyKey,
        new ShopperDetailsCommand(shopperName, shopperEmail(shopperName), countryCode, null, null),
        PSP_CODE,
        new OrderDetailsCommand(
            List.of(
                new OrderLineCommand(merchantReference + "-line", 100L, "EUR", "DIGITAL_GOODS")),
            100L,
            "EUR"));
  }

  private static String shopperEmail(String shopperName) {
    return shopperName + "@example.test";
  }

  private static TaxRateProvider rate() {
    return (country, subdivision, productType) ->
        new TaxRate(country, subdivision, productType, new BigDecimal("0.19"));
  }

  private static TaxRateProvider barrierRate(CyclicBarrier barrier) {
    return (country, subdivision, productType) -> {
      try {
        barrier.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new AssertionError("interrupted before both requests were priced", exception);
      } catch (BrokenBarrierException | TimeoutException exception) {
        throw new AssertionError("both requests did not reach pricing together", exception);
      }
      return new TaxRate(country, subdivision, productType, new BigDecimal("0.19"));
    };
  }

  private int orderCount(String idempotencyKey) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM merchant_order WHERE account_id = ? AND idempotency_key = ?",
            Integer.class,
            merchantAccountId,
            idempotencyKey));
  }

  private int itemCount(String orderReference) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM order_item item "
                + "JOIN merchant_order o ON o.order_id = item.order_id "
                + "WHERE o.order_reference = ?",
            Integer.class,
            orderReference));
  }

  private static long account(
      JdbcTemplate seed, AccountTypes type, String code, @Nullable Long parentAccountId) {
    return Objects.requireNonNull(
        seed.queryForObject(
            "INSERT INTO account "
                + "(account_type_id, parent_account_id, code, name, is_active, created_ts) "
                + "VALUES (?, ?, ?, ?, true, now()) RETURNING account_id",
            Long.class,
            type.getValue().getAccountTypeId(),
            parentAccountId,
            code,
            code));
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }

  private record ConcurrentAttempt(
      String shopperName, @Nullable CreateOrderResult result, boolean conflicted) {}

  /** A PSP that records every order it is asked to create and accepts each one. */
  private static final class RecordingPsp implements PspClient {
    private final List<CreatePspOrderRequest> requests = new ArrayList<>();

    @Override
    public com.outpost.integration.psp.CreatePspOrderResult createOrder(
        CreatePspOrderRequest request) {
      requests.add(request);
      return new com.outpost.integration.psp.CreatePspOrderResult(
          AcceptingPsp.PSP_REFERENCE, AcceptingPsp.PAYMENT_LINK, PspResultCodes.ACCEPTED);
    }

    @Override
    public RefundPspOrderResult refund(RefundPspOrderRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  /** A PSP whose answer never arrives. */
  private static final class UnreachablePsp implements PspClient {
    @Override
    public com.outpost.integration.psp.CreatePspOrderResult createOrder(
        CreatePspOrderRequest request) {
      throw new UnknownPspResultException(new IllegalStateException("the PSP did not answer"));
    }

    @Override
    public RefundPspOrderResult refund(RefundPspOrderRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class AcceptingPsp implements PspClient {
    private static final String PSP_REFERENCE = "psp-accepted";
    private static final String PAYMENT_LINK = "https://pay.example/accepted";

    @Override
    public com.outpost.integration.psp.CreatePspOrderResult createOrder(
        CreatePspOrderRequest request) {
      return new com.outpost.integration.psp.CreatePspOrderResult(
          PSP_REFERENCE, PAYMENT_LINK, PspResultCodes.ACCEPTED);
    }

    @Override
    public RefundPspOrderResult refund(RefundPspOrderRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
