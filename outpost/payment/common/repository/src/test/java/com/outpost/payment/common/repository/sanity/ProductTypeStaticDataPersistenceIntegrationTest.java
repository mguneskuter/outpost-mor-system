package com.outpost.payment.common.repository.sanity;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.payment.common.repository.ProductTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = ProductTypeStaticDataPersistenceIntegrationTest.TestApplication.class)
class ProductTypeStaticDataPersistenceIntegrationTest {
  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ProductTypeStaticDataMapper mapper;

  @Autowired private StaticDataRepository<ProductTypes, ProductType, ProductTypeRecord> repository;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresTestDatabase.registerDataSourceProperties(registry);
  }

  @BeforeEach
  void createProductTypeTable() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS product_type");
    jdbcTemplate.execute(
        "CREATE TABLE product_type ("
            + "product_type_id BIGINT PRIMARY KEY, "
            + "code VARCHAR(64) NOT NULL UNIQUE)");
    jdbcTemplate.update(
        "INSERT INTO product_type (product_type_id, code) VALUES (?, ?)", 1L, "DIGITAL_GOODS");
  }

  @Test
  void mapsSnakeCaseDatabaseColumnsAndRegistersRepository() {
    assertThat(mapper.findAll()).containsExactly(new ProductTypeRecord(1L, "DIGITAL_GOODS"));
    assertThat(repository.findAll()).containsExactly(new ProductTypeRecord(1L, "DIGITAL_GOODS"));
  }

  @SpringBootApplication
  @EnableOutpostPersistence(mapperPackages = "com.outpost.payment.common.repository.sanity")
  static class TestApplication {}
}
