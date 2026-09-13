package com.outpost.gateway;

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
import com.outpost.gateway.order.service.CreateOrderCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.OrderLineCommand;
import com.outpost.gateway.order.service.CreateOrderCommand.ShopperDetailsCommand;
import com.outpost.gateway.order.service.CreateOrderResult;
import com.outpost.gateway.order.service.OrderCreationException;
import com.outpost.gateway.order.service.OrderService;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.RefundRequest;
import com.outpost.integration.psp.service.RefundResult;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.payment.order.LineTaxCalculator;
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

    OrderCreationException refusal =
        catchThrowableOfType(
            OrderCreationException.class, () -> service.create(merchantAccountId, command));

    assertThat(refusal.status()).isEqualTo(422);
    assertThat(refusal.code()).isEqualTo("MISSING_TAX_AUTHORITY");
    assertThat(orderCount("untaxed-key")).isZero();
    assertThat(psp.requests).isEmpty();
  }

  @Test
  void keepsTheStoredOrderWithoutPspFactsAndQueuesNothingWhenThePspAnswerIsLost() {
    TimeOrderedQueue<AccountingQueueRequest> accountingQueue =
        new TimeOrderedQueue<>(Clock.systemUTC(), 10);
    OrderService service = service(rate(), new UnreachablePsp(), accountingQueue);

    OrderCreationException failure =
        catchThrowableOfType(
            OrderCreationException.class,
            () ->
                service.create(merchantAccountId, command("lost-key", "lost-order", "lost", "DE")));

    assertThat(failure.status()).isEqualTo(503);
    assertThat(failure.code()).isEqualTo("PSP_RETRYABLE");
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

    List<Outcome> outcomes = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Outcome> firstOutcome = executor.submit(() -> attempt(service, first));
      Future<Outcome> secondOutcome = executor.submit(() -> attempt(service, second));
      outcomes.add(firstOutcome.get(30, TimeUnit.SECONDS));
      outcomes.add(secondOutcome.get(30, TimeUnit.SECONDS));
    }

    Outcome winner =
        outcomes.stream().filter(outcome -> outcome.result() != null).findFirst().orElseThrow();
    assertThat(outcomes)
        .filteredOn(outcome -> outcome.conflictStatus() == 409)
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
        orders,
        accounts,
        merchantPsps,
        feeConfigurations,
        accountingQueue,
        psp,
        taxRates,
        new LineTaxCalculator());
  }

  private static Outcome attempt(OrderService service, CreateOrderCommand command) {
    String shopperName = command.shopperDetails().fullName();
    try {
      return new Outcome(shopperName, service.create(merchantAccountId, command), 0);
    } catch (OrderCreationException exception) {
      return new Outcome(shopperName, null, exception.status());
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

  private record Outcome(
      String shopperName, @Nullable CreateOrderResult result, int conflictStatus) {}

  /** A PSP that records every order it is asked to create and accepts each one. */
  private static final class RecordingPsp implements PspClient {
    private final List<CreateOrderRequest> requests = new ArrayList<>();

    @Override
    public com.outpost.integration.psp.service.CreateOrderResult createOrder(
        CreateOrderRequest request) {
      requests.add(request);
      return new com.outpost.integration.psp.service.CreateOrderResult(
          AcceptingPsp.PSP_REFERENCE, AcceptingPsp.PAYMENT_LINK, ResultCode.ACCEPTED);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  /** A PSP whose answer never arrives. */
  private static final class UnreachablePsp implements PspClient {
    @Override
    public com.outpost.integration.psp.service.CreateOrderResult createOrder(
        CreateOrderRequest request) {
      throw new IllegalStateException("the PSP did not answer");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class AcceptingPsp implements PspClient {
    private static final String PSP_REFERENCE = "psp-accepted";
    private static final String PAYMENT_LINK = "https://pay.example/accepted";

    @Override
    public com.outpost.integration.psp.service.CreateOrderResult createOrder(
        CreateOrderRequest request) {
      return new com.outpost.integration.psp.service.CreateOrderResult(
          PSP_REFERENCE, PAYMENT_LINK, ResultCode.ACCEPTED);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
