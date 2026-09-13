package com.outpost.gateway.tax.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.common.ProductTypes;
import com.outpost.tax.provider.TaxRateProvider;
import com.outpost.tax.provider.cached.CachedTaxRateProvider;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class MyBatisTaxRateRepositoryIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_tax_rate", "outpost_gateway_tax_rate", "outpost_gateway_tax_rate");
  private static @Nullable HikariDataSource dataSource;
  private static @Nullable MyBatisTaxRateRepository repository;

  @BeforeAll
  static void migrateAndStoreCaliforniaRates() throws Exception {
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
    sessionFactory.setMapperLocations(new ClassPathResource("db/mapper/TaxRateMapper.xml"));
    repository =
        new MyBatisTaxRateRepository(
            new SqlSessionTemplate(Objects.requireNonNull(sessionFactory.getObject()))
                .getMapper(TaxRateMapper.class));

    JdbcTemplate jdbc = new JdbcTemplate(database);
    var unitedStates = Countries.UNITED_STATES.getValue();
    var california = CountrySubdivisions.US_CA.getValue();
    jdbc.update(
        "INSERT INTO country (country_id, iso_code, name) VALUES (?, ?, ?)",
        unitedStates.getCountryId(),
        unitedStates.getIsoCode(),
        unitedStates.getName());
    jdbc.update(
        "INSERT INTO country_subdivision (country_subdivision_id, country_id, code, name) "
            + "VALUES (?, ?, ?, ?)",
        california.getCountrySubdivisionId(),
        unitedStates.getCountryId(),
        california.getCode(),
        california.getName());
    for (ProductTypes productType : ProductTypes.values()) {
      jdbc.update(
          "INSERT INTO product_type (product_type_id, code) VALUES (?, ?)",
          productType.getValue().getProductTypeId(),
          productType.getValue().getCode());
    }
    jdbc.update(
        "INSERT INTO tax_rate (country_id, country_subdivision_id, rate) VALUES (?, ?, ?)",
        unitedStates.getCountryId(),
        california.getCountrySubdivisionId(),
        new BigDecimal("0.0725"));
    jdbc.update(
        "INSERT INTO tax_rate (country_id, country_subdivision_id, product_type_id, rate) "
            + "VALUES (?, ?, (SELECT product_type_id FROM product_type WHERE code = ?), ?)",
        unitedStates.getCountryId(),
        california.getCountrySubdivisionId(),
        ProductTypes.DIGITAL_GOODS.getValue().getCode(),
        new BigDecimal("0.0000"));
  }

  @AfterAll
  static void closeDatabase() {
    if (dataSource != null) {
      dataSource.close();
    }
    DATABASE.stop();
  }

  @Test
  void resolvesStoredProductTypeRateAheadOfJurisdictionRate() {
    var rate =
        provider()
            .getRate(
                Countries.UNITED_STATES.getValue(),
                CountrySubdivisions.US_CA.getValue(),
                ProductTypes.DIGITAL_GOODS.getValue());

    assertThat(rate.rate()).isEqualByComparingTo("0.0000");
  }

  @Test
  void resolvesJurisdictionRateForProductTypeWithoutStoredRate() {
    var rate =
        provider()
            .getRate(
                Countries.UNITED_STATES.getValue(),
                CountrySubdivisions.US_CA.getValue(),
                ProductTypes.PHYSICAL_GOODS.getValue());

    assertThat(rate.rate()).isEqualByComparingTo("0.0725");
  }

  private static TaxRateProvider provider() {
    return new CachedTaxRateProvider(Objects.requireNonNull(repository));
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
