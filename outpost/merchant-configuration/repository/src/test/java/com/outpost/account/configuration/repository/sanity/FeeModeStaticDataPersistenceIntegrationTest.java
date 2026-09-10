package com.outpost.account.configuration.repository.sanity;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.FeeModes.FeeMode;
import com.outpost.account.configuration.repository.FeeModeRecord;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = FeeModeStaticDataPersistenceIntegrationTest.TestApplication.class)
class FeeModeStaticDataPersistenceIntegrationTest {
  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private FeeModeStaticDataMapper mapper;

  @Autowired private StaticDataRepository<FeeModes, FeeMode, FeeModeRecord> repository;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresTestDatabase.registerDataSourceProperties(registry);
  }

  @BeforeEach
  void createFeeModeTable() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS fee_mode");
    jdbcTemplate.execute(
        "CREATE TABLE fee_mode ("
            + "fee_mode_id BIGINT PRIMARY KEY, "
            + "code VARCHAR(64) NOT NULL UNIQUE)");
    jdbcTemplate.update("INSERT INTO fee_mode (fee_mode_id, code) VALUES (?, ?)", 1L, "PERCENTAGE");
  }

  @Test
  void mapsSnakeCaseDatabaseColumnsAndRegistersRepository() {
    assertThat(mapper.findAll()).containsExactly(new FeeModeRecord(1L, "PERCENTAGE"));
    assertThat(repository.findAll()).containsExactly(new FeeModeRecord(1L, "PERCENTAGE"));
  }

  @SpringBootApplication
  @EnableOutpostPersistence(mapperPackages = "com.outpost.account.configuration.repository.sanity")
  static class TestApplication {}
}
