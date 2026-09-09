package com.outpost.framework.persistence;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Shared persistence conventions that a deployable opts into with {@link EnableOutpostPersistence}.
 *
 * <p>Spring Boot's datasource, MyBatis and transaction auto-configurations create the single Hikari
 * pool, {@code SqlSessionFactory} and {@code PlatformTransactionManager} from the deployable's
 * {@code spring.datasource.*} properties. This configuration registers the shared handcrafted XML
 * mapper location and fails fast at startup when the deployable-provided datasource is unavailable;
 * it never creates or wraps a datasource itself.
 */
@Configuration(proxyBeanMethods = false)
class OutpostPersistenceConfiguration {

  private static final String MAPPER_XML_LOCATION = "classpath*:db/mapper/*.xml";

  @Bean
  static OutpostDataSourceConnectionValidator outpostDataSourceConnectionValidator(
      DataSource dataSource) {
    return new OutpostDataSourceConnectionValidator(dataSource);
  }

  /**
   * Points the shared MyBatis {@code SqlSessionFactory} at every module's {@code db/mapper} XML
   * files.
   */
  @Bean
  static SqlSessionFactoryBeanCustomizer outpostMapperLocationsCustomizer() {
    return factory -> factory.setMapperLocations(resolveMapperXmlFiles());
  }

  private static Resource[] resolveMapperXmlFiles() {
    try {
      return new PathMatchingResourcePatternResolver().getResources(MAPPER_XML_LOCATION);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Could not resolve MyBatis mapper locations: " + MAPPER_XML_LOCATION, exception);
    }
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
