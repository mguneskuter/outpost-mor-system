package com.outpost.framework.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Enables Outpost's shared persistence conventions on a deployable.
 *
 * <p>It registers the shared mapper-discovery mechanism, which registers only interfaces marked
 * with {@link RegisteredMapper}, and validates at startup that a usable datasource is available.
 * The datasource itself is always created by Spring Boot's {@link DataSourceAutoConfiguration} from
 * the deployable's own {@code spring.datasource.*} properties; this configuration never creates or
 * wraps a datasource.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({OutpostPersistenceConfiguration.class, OutpostMapperRegistrar.class})
public @interface EnableOutpostPersistence {
  /** Additional packages scanned for interfaces annotated with {@link RegisteredMapper}. */
  String[] mapperPackages() default {};
}
