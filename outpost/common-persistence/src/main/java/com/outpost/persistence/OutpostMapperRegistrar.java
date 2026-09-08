package com.outpost.persistence;

import static org.springframework.util.ClassUtils.getPackageName;

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

/**
 * Registers a MyBatis mapper scanner that discovers only interfaces annotated with {@link
 * CommonMapper}.
 *
 * <p>The scanner base package is the package of the class that declares {@link
 * EnableOutpostPersistence}, which is the deployable's application class. Mappers and the SQL they
 * own belong to that deployable.
 */
class OutpostMapperRegistrar implements ImportBeanDefinitionRegistrar {

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    String basePackage = getPackageName(importingClassMetadata.getClassName());
    if (!StringUtils.hasText(basePackage)) {
      throw new IllegalStateException(
          "EnableOutpostPersistence cannot determine a base package to scan for mappers");
    }
    BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
    builder.addPropertyValue("processPropertyPlaceHolders", true);
    builder.addPropertyValue("annotationClass", CommonMapper.class);
    builder.addPropertyValue("basePackage", basePackage);
    builder.addPropertyValue("sqlSessionFactoryBeanName", "sqlSessionFactory");
    builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    registry.registerBeanDefinition(
        MapperScannerConfigurer.class.getName(), builder.getBeanDefinition());
  }
}
