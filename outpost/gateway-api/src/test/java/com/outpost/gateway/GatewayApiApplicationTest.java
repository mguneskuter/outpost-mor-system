package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.persistence.testfixtures.PostgresTestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class GatewayApiApplicationTest {

  private final ApplicationContext context;
  private final DataSource dataSource;

  @Autowired
  GatewayApiApplicationTest(ApplicationContext context, DataSource dataSource) {
    this.context = context;
    this.dataSource = dataSource;
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresTestDatabase.registerDataSourceProperties(registry);
  }

  @Test
  void startsWithValidDatasourceProperties() {
    assertThat(context).isNotNull();
  }

  @Test
  void createsSingleHikariDatasourceFromDeployableProperties() {
    assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
  }

  @Test
  void exposesNoBusinessMappersOrRepositories() {
    assertThat(context.getBeansWithAnnotation(com.outpost.persistence.RegisteredMapper.class))
        .isEmpty();
  }
}
