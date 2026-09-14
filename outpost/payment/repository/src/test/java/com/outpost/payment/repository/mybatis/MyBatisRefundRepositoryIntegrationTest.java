package com.outpost.payment.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.ShopperDetail;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.refund.Refund;
import com.outpost.payment.refund.RefundItem;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class MyBatisRefundRepositoryIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_refund_repository", "outpost_refund_repository", "outpost_refund_repository");
  private static @Nullable HikariDataSource dataSource;
  private static @Nullable MyBatisOrderRepository orders;
  private static @Nullable MyBatisRefundRepository refunds;
  private static @Nullable JdbcTemplate jdbcTemplate;
  private static @Nullable Account merchantAccount;
  private static @Nullable Account pspAccount;

  @BeforeAll
  static void migrateAndSeed() throws Exception {
    DATABASE.start();
    HikariDataSource database =
        DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .url(DATABASE.getJdbcUrl())
            .username(DATABASE.getUsername())
            .password(DATABASE.getPassword())
            .build();
    dataSource = database;
    Flyway.configure()
        .dataSource(database)
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .load()
        .migrate();
    SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
    sessionFactory.setDataSource(database);
    sessionFactory.setMapperLocations(
        new ClassPathResource("db/mapper/payment/OrderMapper.xml"),
        new ClassPathResource("db/mapper/payment/RefundMapper.xml"));
    SqlSessionTemplate sqlSession =
        new SqlSessionTemplate(Objects.requireNonNull(sessionFactory.getObject()));
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(database);
    refunds = new MyBatisRefundRepository(sqlSession, transactionManager);
    JdbcTemplate jdbc = new JdbcTemplate(database);
    jdbcTemplate = jdbc;
    seedReferenceData(jdbc);
    Account merchant =
        KnownAccounts.underRoot(
            account(jdbc, AccountTypes.MERCHANT, "REFUND_MERCHANT"),
            AccountTypes.MERCHANT,
            "REFUND_MERCHANT");
    Account psp =
        KnownAccounts.underRoot(
            account(jdbc, AccountTypes.PSP, "REFUND_PSP"), AccountTypes.PSP, "REFUND_PSP");
    merchantAccount = merchant;
    pspAccount = psp;
    orders =
        new MyBatisOrderRepository(
            sqlSession, transactionManager, new KnownAccounts(merchant, psp));
  }

  @AfterAll
  static void closeDatabase() {
    if (dataSource != null) {
      dataSource.close();
    }
    DATABASE.stop();
  }

  @Test
  void insertingRefundReturnsItAndItsItemsWithIdsAndTheWritingTransactionTime() {
    Order order = storedOrder("dated");
    new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(dataSource)))
        .executeWithoutResult(
            status -> {
              Refund stored =
                  refunds().insertRefund(unsavedRefund("refund-dated", order)).orElseThrow();

              Instant transactionTime =
                  Objects.requireNonNull(jdbc().queryForObject("SELECT now()", Instant.class));
              assertThat(stored.refundId()).isNotNull().isPositive();
              assertThat(stored.createdAt()).isEqualTo(transactionTime);
              assertThat(stored.pspRefundReference()).isNull();
              assertThat(stored.items())
                  .hasSize(2)
                  .allSatisfy(item -> assertThat(item.refundItemId()).isNotNull().isPositive())
                  .allSatisfy(item -> assertThat(item.isRefundFailed()).isFalse());
              assertThat(stored.items().stream().map(RefundItem::orderItem))
                  .containsExactlyElementsOf(order.getItems());
              assertThat(refunds().findRefundByRefundReference("refund-dated")).contains(stored);
              assertThat(refunds().findRefundsByOriginalReference(order.getOrderReference()))
                  .containsExactly(stored);
            });
  }

  @Test
  void insertClaimingAnAlreadyClaimedLineStoresNothing() {
    Order order = storedOrder("claimed");
    store(unsavedRefund("refund-claimed-first", order, 0));

    Optional<Refund> second = refunds().insertRefund(unsavedRefund("refund-claimed-second", order));

    assertThat(second).isEmpty();
    assertThat(refunds().findRefundByRefundReference("refund-claimed-second")).isEmpty();
    assertThat(refundItemCount(order)).isEqualTo(1);
  }

  @Test
  void releasesTheLinesOfRefundMarkedFailed() {
    Order order = storedOrder("released");
    store(unsavedRefund("refund-released-first", order));

    refunds().updateRefundItemRefundFailed("refund-released-first");
    Optional<Refund> second =
        refunds().insertRefund(unsavedRefund("refund-released-second", order));

    assertThat(second).isPresent();
    assertThat(refunds().findRefundByRefundReference("refund-released-first").orElseThrow().items())
        .allSatisfy(item -> assertThat(item.isRefundFailed()).isTrue());
  }

  @Test
  void storesThePspRefundReferenceOnce() {
    Order order = storedOrder("acknowledged");
    store(unsavedRefund("refund-acknowledged", order));

    refunds().updateRefundPspRefundReference("refund-acknowledged", "psp-refund-77");
    refunds().updateRefundPspRefundReference("refund-acknowledged", "psp-refund-77");

    assertThat(
            refunds()
                .findRefundByRefundReference("refund-acknowledged")
                .orElseThrow()
                .pspRefundReference())
        .isEqualTo("psp-refund-77");
    assertThatThrownBy(
            () -> refunds().updateRefundPspRefundReference("refund-acknowledged", "psp-refund-78"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void exactlyOneOfTwoConcurrentRefundsClaimsTheSameLine() throws Exception {
    Order order = storedOrder("raced");
    CyclicBarrier bothReady = new CyclicBarrier(2);

    List<Optional<Refund>> stored;
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Optional<Refund>> first =
          executor.submit(() -> claim(bothReady, unsavedRefund("refund-raced-first", order)));
      Future<Optional<Refund>> second =
          executor.submit(() -> claim(bothReady, unsavedRefund("refund-raced-second", order)));
      stored = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
    }

    assertThat(stored).filteredOn(Optional::isPresent).hasSize(1);
    assertThat(refundItemCount(order)).isEqualTo(2);
    assertThat(refunds().findRefundsByOriginalReference(order.getOrderReference())).hasSize(1);
  }

  private static Optional<Refund> claim(CyclicBarrier bothReady, Refund refund) throws Exception {
    bothReady.await(10, TimeUnit.SECONDS);
    return refunds().insertRefund(refund);
  }

  @Test
  void rejectsSecondRefundWithTheSameRefundReference() {
    Order order = storedOrder("twice");
    refunds().insertRefund(unsavedRefund("refund-twice", order, 0));

    assertThatThrownBy(() -> refunds().insertRefund(unsavedRefund("refund-twice", order, 1)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsDeletingAnItemAndClearingItsFailure() {
    Order order = storedOrder("fixed");
    store(unsavedRefund("refund-fixed", order));
    refunds().updateRefundItemRefundFailed("refund-fixed");
    long refundId =
        Objects.requireNonNull(
            refunds().findRefundByRefundReference("refund-fixed").orElseThrow().refundId());

    assertThatThrownBy(() -> jdbc().update("DELETE FROM refund_item WHERE refund_id = ?", refundId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc()
                    .update(
                        "UPDATE refund_item SET refund_failed = false WHERE refund_id = ?",
                        refundId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () -> jdbc().update("DELETE FROM merchant_refund WHERE refund_id = ?", refundId))
        .isInstanceOf(DataAccessException.class);
  }

  /** Stores a refund whose lines are free, failing the test if they are not. */
  private static Refund store(Refund refund) {
    return refunds().insertRefund(refund).orElseThrow();
  }

  private static MyBatisRefundRepository refunds() {
    return Objects.requireNonNull(refunds);
  }

  private static JdbcTemplate jdbc() {
    return Objects.requireNonNull(jdbcTemplate);
  }

  private int refundItemCount(Order order) {
    return Objects.requireNonNull(
        jdbc()
            .queryForObject(
                "SELECT count(*) FROM refund_item JOIN order_item USING (order_item_id) "
                    + "WHERE order_item.order_id = ?",
                Integer.class,
                order.getOrderId().orElseThrow()));
  }

  /** A refund of every line of {@code order}. */
  private static Refund unsavedRefund(String refundReference, Order order) {
    return refund(
        refundReference,
        order,
        order.getItems().stream().map(item -> new RefundItem(null, item, false)).toList());
  }

  /** A refund of one line of {@code order}. */
  private static Refund unsavedRefund(String refundReference, Order order, int line) {
    return refund(
        refundReference, order, List.of(new RefundItem(null, order.getItems().get(line), false)));
  }

  private static Refund refund(String refundReference, Order order, List<RefundItem> items) {
    return new Refund(
        null,
        refundReference,
        order.getOrderId().orElseThrow(),
        order.getOrderReference(),
        "merchant-" + refundReference,
        refundReference + "-key",
        null,
        items,
        null);
  }

  private static Order storedOrder(String name) {
    ShopperDetail shopper =
        new ShopperDetail(
            null,
            name + "@example.test",
            "Shopper " + name,
            Countries.GERMANY.getValue(),
            null,
            null);
    return Objects.requireNonNull(orders).insertOrder(shopper, anOrder(name)).orElseThrow();
  }

  private static Order anOrder(String name) {
    return new Order(
        null,
        "refund-order-" + name,
        "merchant-refund-order-" + name,
        Objects.requireNonNull(merchantAccount),
        null,
        Countries.GERMANY.getValue(),
        null,
        eur(100L),
        eur(19L),
        eur(119L),
        "refund-order-" + name + "-key",
        "refund-order-" + name + "-fingerprint",
        Objects.requireNonNull(pspAccount),
        "41",
        "https://pay.example/" + name,
        null,
        List.of(
            new OrderItem(
                null,
                ProductTypes.DIGITAL_GOODS.getValue(),
                name + "-line-1",
                name + "-merchant-line-1",
                eur(60L),
                eur(11L),
                new BigDecimal("0.1900")),
            new OrderItem(
                null,
                ProductTypes.DIGITAL_GOODS.getValue(),
                name + "-line-2",
                name + "-merchant-line-2",
                eur(40L),
                eur(8L),
                new BigDecimal("0.1900"))));
  }

  private static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  private static void seedReferenceData(JdbcTemplate jdbc) {
    var country = Countries.GERMANY.getValue();
    jdbc.update(
        "INSERT INTO country (country_id, iso_code, name) VALUES (?, ?, ?)",
        country.getCountryId(),
        country.getIsoCode(),
        country.getName());
    var currency = Currencies.EUR.getValue();
    jdbc.update(
        "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
        currency.getCurrencyId(),
        currency.getCurrencyCode(),
        currency.getExponent());
    var productType = ProductTypes.DIGITAL_GOODS.getValue();
    jdbc.update(
        "INSERT INTO product_type (product_type_id, code) VALUES (?, ?)",
        productType.getProductTypeId(),
        productType.getCode());
    for (AccountTypes type : List.of(AccountTypes.MERCHANT, AccountTypes.PSP)) {
      jdbc.update(
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          type.getValue().getAccountTypeId(),
          type.getValue().getCode());
    }
  }

  private static long account(JdbcTemplate jdbc, AccountTypes type, String code) {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "INSERT INTO account (account_type_id, code, name, is_active, created_ts) "
                + "VALUES (?, ?, ?, true, now()) RETURNING account_id",
            Long.class,
            type.getValue().getAccountTypeId(),
            code,
            code));
  }
}
