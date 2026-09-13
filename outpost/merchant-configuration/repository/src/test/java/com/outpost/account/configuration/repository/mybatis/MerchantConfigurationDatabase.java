package com.outpost.account.configuration.repository.mybatis;

import com.outpost.account.AccountTypes;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** A migrated PostgreSQL database with one mapper XML loaded, owned by one test class. */
final class MerchantConfigurationDatabase implements AutoCloseable {
  private final PostgreSQLContainer<?> container;
  private final HikariDataSource dataSource;
  private final SqlSessionTemplate sqlSession;

  MerchantConfigurationDatabase(String name, String mapperXml) throws Exception {
    container = PostgresTestDatabase.startContainer(name, name, name);
    container.start();
    dataSource =
        DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .url(container.getJdbcUrl())
            .username(container.getUsername())
            .password(container.getPassword())
            .build();
    Flyway.configure()
        .dataSource(dataSource)
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
    sessionFactory.setDataSource(dataSource);
    sessionFactory.setMapperLocations(new ClassPathResource(mapperXml));
    sqlSession = new SqlSessionTemplate(Objects.requireNonNull(sessionFactory.getObject()));
  }

  SqlSessionTemplate sqlSession() {
    return sqlSession;
  }

  JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  long account(AccountTypes type, String code) {
    JdbcTemplate jdbc = jdbc();
    jdbc.update(
        "INSERT INTO account_type (account_type_id, code) VALUES (?, ?) ON CONFLICT DO NOTHING",
        type.getValue().getAccountTypeId(),
        type.getValue().getCode());
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "INSERT INTO account (account_type_id, code, name, is_active, created_ts) "
                + "VALUES (?, ?, ?, true, now()) RETURNING account_id",
            Long.class,
            type.getValue().getAccountTypeId(),
            code,
            code));
  }

  @Override
  public void close() {
    dataSource.close();
    container.stop();
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
