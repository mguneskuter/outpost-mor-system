package com.outpost.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared persistence conventions that a deployable opts into with {@link EnableOutpostPersistence}.
 *
 * <p>Spring Boot's datasource, MyBatis and transaction auto-configurations create the single Hikari
 * pool, {@code SqlSessionFactory} and {@code PlatformTransactionManager} from the deployable's
 * {@code spring.datasource.*} properties. This configuration fails fast at startup when the
 * deployable-provided datasource is unavailable; it never creates or wraps a datasource itself.
 */
@Configuration(proxyBeanMethods = false)
class OutpostPersistenceConfiguration {

  @Bean
  static OutpostDataSourceConnectionValidator outpostDataSourceConnectionValidator(
      DataSource dataSource) {
    return new OutpostDataSourceConnectionValidator(dataSource);
  }

  static final class OutpostDataSourceConnectionValidator implements InitializingBean {

    private final DataSource dataSource;

    OutpostDataSourceConnectionValidator(DataSource dataSource) {
      this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
      try (Connection connection = this.dataSource.getConnection()) {
        if (!connection.isValid(5)) {
          throw new IllegalStateException("Outpost datasource did not accept a valid connection");
        }
      } catch (SQLException exception) {
        throw new IllegalStateException(
            "Could not open a connection to the Outpost datasource", exception);
      }
    }
  }
}
