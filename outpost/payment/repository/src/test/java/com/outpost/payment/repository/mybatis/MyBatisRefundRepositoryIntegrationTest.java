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
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
  private static @Nullable MyBatisRefundRepository refunds;
  private static @Nullable JdbcTemplate jdbcTemplate;
  private static long orderId;

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
    refunds = new MyBatisRefundRepository(sqlSession.getMapper(RefundMapper.class));
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
    orderId =
        new MyBatisOrderRepository(
                sqlSession,
                new DataSourceTransactionManager(database),
                new KnownAccounts(merchant, psp))
            .insertOrder(shopper(), anOrder(merchant, psp))
            .orElseThrow()
            .getOrderId()
            .orElseThrow();
  }

  @AfterAll
  static void closeDatabase() {
    if (dataSource != null) {
      dataSource.close();
    }
    DATABASE.stop();
  }

  @Test
  void insertingRefundReturnsItWithItsIdAndTheWritingTransactionTime() {
    new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(dataSource)))
        .executeWithoutResult(
            status -> {
              Refund stored = refunds().insertRefund(unsavedRefund("refund-dated"));

              Instant transactionTime =
                  Objects.requireNonNull(jdbc().queryForObject("SELECT now()", Instant.class));
              assertThat(stored.refundId()).isNotNull().isPositive();
              assertThat(stored.createdAt()).isEqualTo(transactionTime);
              assertThat(stored.refundReference()).isEqualTo("refund-dated");
              assertThat(stored.orderId()).isEqualTo(orderId);
              assertThat(
                      jdbc()
                          .queryForObject(
                              "SELECT created_ts FROM merchant_refund WHERE refund_reference = ?",
                              Instant.class,
                              "refund-dated"))
                  .isEqualTo(transactionTime);
            });
  }

  @Test
  void rejectsSecondRefundWithTheSameRefundReference() {
    refunds().insertRefund(unsavedRefund("refund-twice"));

    assertThatThrownBy(() -> refunds().insertRefund(unsavedRefund("refund-twice")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsChangingOrDeletingStoredRefund() {
    refunds().insertRefund(unsavedRefund("refund-fixed"));

    assertThatThrownBy(
            () ->
                jdbc()
                    .update(
                        "UPDATE merchant_refund SET merchant_reference = 'other' "
                            + "WHERE refund_reference = ?",
                        "refund-fixed"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc()
                    .update(
                        "DELETE FROM merchant_refund WHERE refund_reference = ?", "refund-fixed"))
        .isInstanceOf(DataAccessException.class);
  }

  private static MyBatisRefundRepository refunds() {
    return Objects.requireNonNull(refunds);
  }

  private static JdbcTemplate jdbc() {
    return Objects.requireNonNull(jdbcTemplate);
  }

  private static Refund unsavedRefund(String refundReference) {
    return new Refund(
        null,
        refundReference,
        orderId,
        "refund-order",
        "merchant-" + refundReference,
        refundReference + "-key",
        "77",
        null);
  }

  private static ShopperDetail shopper() {
    return new ShopperDetail(
        null, "refund@example.test", "Refund Shopper", Countries.GERMANY.getValue(), null, null);
  }

  private static Order anOrder(Account merchantAccount, Account pspAccount) {
    return new Order(
        null,
        "refund-order",
        "merchant-refund-order",
        merchantAccount,
        null,
        Countries.GERMANY.getValue(),
        null,
        eur(100L),
        eur(19L),
        eur(119L),
        "refund-order-key",
        "refund-order-fingerprint",
        pspAccount,
        "41",
        "https://pay.example/refund-order",
        null,
        List.of(
            new OrderItem(
                null,
                ProductTypes.DIGITAL_GOODS.getValue(),
                "refund-order-line",
                "merchant-refund-order-line",
                eur(100L),
                eur(19L),
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
