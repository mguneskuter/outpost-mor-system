package com.outpost.framework.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.framework.persistence.testservices.TestMapper;
import com.outpost.framework.persistence.testservices.TestXmlMapper;
import com.outpost.framework.persistence.testservices.TransactionalTestService;
import com.outpost.framework.persistence.testservices.UnmarkedMapper;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = PersistenceTestApplication.class)
class PersistenceIntegrationTest {

  private final ApplicationContext context;
  private final DataSource dataSource;
  private final TestMapper testMapper;
  private final TestXmlMapper testXmlMapper;
  private final TransactionalTestService transactionalTestService;

  @Autowired
  PersistenceIntegrationTest(
      ApplicationContext context,
      DataSource dataSource,
      TestMapper testMapper,
      TestXmlMapper testXmlMapper,
      TransactionalTestService transactionalTestService) {
    this.context = context;
    this.dataSource = dataSource;
    this.testMapper = testMapper;
    this.testXmlMapper = testXmlMapper;
    this.transactionalTestService = transactionalTestService;
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresTestDatabase.registerDataSourceProperties(registry);
  }

  @Test
  void usesExactlyOneHikariDatasourceFromProperties() {
    assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    HikariDataSource hikari = (HikariDataSource) dataSource;
    assertThat(hikari.getJdbcUrl()).startsWith("jdbc:postgresql://");
    assertThat(hikari.getUsername()).isEqualTo("outpost_test");
  }

  @Test
  void registersOnlyMarkedMapper() {
    assertThat(context.getBean(TestMapper.class)).isNotNull();
    assertThat(context.getBeansOfType(UnmarkedMapper.class)).isEmpty();
  }

  @Test
  void executesQueryViaMyBatisAndParticipatesInSpringTransaction() {
    assertThat(testMapper.one()).isEqualTo(1);
    assertThat(transactionalTestService.runsInTransactionAndQueries()).isTrue();
  }

  @Test
  void loadsHandcraftedXmlMapperFromDbMapperFolder() {
    assertThat(testXmlMapper.oneViaXml()).isEqualTo(1);
  }

  @Test
  void providesTransactionManagerForTheDatasource() {
    assertThat(
            context.getBeansOfType(
                org.springframework.transaction.PlatformTransactionManager.class))
        .hasSize(1);
  }
}
