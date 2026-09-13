package com.outpost.payment.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.AccountTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.ShopperDetail;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

class MyBatisOrderRepositoryIntegrationTest {
  private static final Instant CREATED = Instant.parse("2026-09-12T00:00:00Z");
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_order_repository", "outpost_order_repository", "outpost_order_repository");
  private static @Nullable HikariDataSource dataSource;
  private static @Nullable MyBatisOrderRepository orders;
  private static @Nullable JdbcTemplate jdbcTemplate;
  private static long merchantAccountId;
  private static long pspAccountId;

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
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
    sessionFactory.setDataSource(database);
    sessionFactory.setMapperLocations(new ClassPathResource("db/mapper/payment/OrderMapper.xml"));
    orders =
        new MyBatisOrderRepository(
            new SqlSessionTemplate(Objects.requireNonNull(sessionFactory.getObject())),
            new DataSourceTransactionManager(database));
    JdbcTemplate jdbc = new JdbcTemplate(database);
    jdbcTemplate = jdbc;
    seedReferenceData(jdbc);
    merchantAccountId = account(jdbc, AccountTypes.MERCHANT, "REPOSITORY_MERCHANT");
    pspAccountId = account(jdbc, AccountTypes.PSP, "REPOSITORY_PSP");
  }

  @AfterAll
  static void closeDatabase() {
    if (dataSource != null) {
      dataSource.close();
    }
    DATABASE.stop();
  }

  @Test
  void insertingAnOrderReturnsItAsStoredWithItsIds() {
    Order stored =
        orders()
            .insertOrder(shopper("stored", "Stored Shopper"), anOrderWithTwoLines("stored"))
            .orElseThrow();

    assertThat(stored.getOrderId()).isPresent();
    assertThat(stored.getShopperId()).isPresent();
    assertThat(stored.getItems()).allSatisfy(item -> assertThat(item.getOrderItemId()).isPresent());
    assertThat(stored.getShopperCountrySubdivision())
        .contains(CountrySubdivisions.US_CA.getValue());
    assertThat(orders().findOrderByIdempotencyKey(merchantAccountId, "stored-key"))
        .contains(stored);
  }

  @Test
  void anOrderLosingItsIdempotencyKeyStoresNoShopperAndNoOrder() {
    assertThat(orders().insertOrder(shopper("winner", "Winner"), anOrderWithTwoLines("race")))
        .isPresent();

    Optional<Order> loser =
        orders().insertOrder(shopper("loser", "Loser"), anOrderWithTwoLines("race", "loser-order"));

    assertThat(loser).isEmpty();
    assertThat(count("SELECT count(*) FROM shopper_detail WHERE email = ?", email("loser")))
        .isZero();
    assertThat(count("SELECT count(*) FROM merchant_order WHERE idempotency_key = ?", "race-key"))
        .isEqualTo(1);
    assertThat(count("SELECT count(*) FROM order_item WHERE order_line_reference LIKE ?", "loser%"))
        .isZero();
  }

  @Test
  void anExistingShopperEmailKeepsThatShopperAndItsDetails() {
    Order first =
        orders()
            .insertOrder(shopper("returning", "First Name"), anOrderWithTwoLines("returning-1"))
            .orElseThrow();

    Order second =
        orders()
            .insertOrder(shopper("returning", "Second Name"), anOrderWithTwoLines("returning-2"))
            .orElseThrow();

    assertThat(second.getShopperId()).isEqualTo(first.getShopperId());
    assertThat(
            jdbc()
                .queryForList(
                    "SELECT full_name FROM shopper_detail WHERE email = ?",
                    String.class,
                    email("returning")))
        .containsExactly("First Name");
  }

  private static MyBatisOrderRepository orders() {
    return Objects.requireNonNull(orders);
  }

  private static JdbcTemplate jdbc() {
    return Objects.requireNonNull(jdbcTemplate);
  }

  private static ShopperDetail shopper(String slug, String fullName) {
    return new ShopperDetail(
        null,
        email(slug),
        fullName,
        Countries.UNITED_STATES.getValue(),
        CountrySubdivisions.US_CA.getValue(),
        "94105");
  }

  private static Order anOrderWithTwoLines(String slug) {
    return anOrderWithTwoLines(slug, slug);
  }

  private static Order anOrderWithTwoLines(String keySlug, String referenceSlug) {
    return new Order(
        null,
        referenceSlug + "-order",
        referenceSlug + "-merchant-order",
        merchantAccountId,
        null,
        Countries.UNITED_STATES.getValue(),
        CountrySubdivisions.US_CA.getValue(),
        usd(300L),
        usd(21L),
        usd(321L),
        keySlug + "-key",
        referenceSlug + "-fingerprint",
        referenceSlug + "-payment",
        pspAccountId,
        null,
        null,
        CREATED,
        List.of(
            line(referenceSlug + "-line-1", 100L, 7L), line(referenceSlug + "-line-2", 200L, 14L)));
  }

  private static OrderItem line(String orderLineReference, long net, long tax) {
    return new OrderItem(
        null,
        ProductTypes.DIGITAL_GOODS.getValue(),
        orderLineReference,
        "merchant-" + orderLineReference,
        usd(net),
        usd(tax),
        new BigDecimal("0.0725"));
  }

  private static Amount usd(long quantity) {
    return new Amount(Currencies.USD.getValue(), quantity);
  }

  private static String email(String slug) {
    return slug + "@example.test";
  }

  private static int count(String sql, String value) {
    return Objects.requireNonNull(jdbc().queryForObject(sql, Integer.class, value));
  }

  private static void seedReferenceData(JdbcTemplate jdbc) {
    var country = Countries.UNITED_STATES.getValue();
    jdbc.update(
        "INSERT INTO country (country_id, iso_code, name) VALUES (?, ?, ?)",
        country.getCountryId(),
        country.getIsoCode(),
        country.getName());
    var subdivision = CountrySubdivisions.US_CA.getValue();
    jdbc.update(
        "INSERT INTO country_subdivision (country_subdivision_id, country_id, code, name) "
            + "VALUES (?, ?, ?, ?)",
        subdivision.getCountrySubdivisionId(),
        country.getCountryId(),
        subdivision.getCode(),
        subdivision.getName());
    var currency = Currencies.USD.getValue();
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

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
