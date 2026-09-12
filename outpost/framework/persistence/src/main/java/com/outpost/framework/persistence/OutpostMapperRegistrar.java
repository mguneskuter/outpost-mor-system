package com.outpost.framework.persistence;

import static org.springframework.util.ClassUtils.getPackageName;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

/**
 * Registers a MyBatis mapper scanner that discovers only interfaces annotated with {@link
 * RegisteredMapper}.
 *
 * <p>The scanner base package is the package of the class that declares {@link
 * EnableOutpostPersistence}, which is the deployable's application class. Mapper interfaces and the
 * handcrafted XML files that implement them belong to the deployable or a persistence adapter; the
 * shared {@code mybatis.mapper-locations} default points MyBatis at every module's {@code
 * db/mapper} folder.
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
    Map<String, Object> attributes =
        importingClassMetadata.getAnnotationAttributes(EnableOutpostPersistence.class.getName());
    String[] mapperPackages =
        attributes != null ? (String[]) attributes.get("mapperPackages") : new String[0];
    Set<String> packages = new LinkedHashSet<>();
    packages.add(basePackage);
    packages.addAll(Arrays.asList(mapperPackages));
    String scannerBeanName = MapperScannerConfigurer.class.getName();
    if (registry.containsBeanDefinition(scannerBeanName)) {
      BeanDefinition scanner = registry.getBeanDefinition(scannerBeanName);
      Set<String> mergedPackages = new LinkedHashSet<>();
      Object existingPackages = scanner.getPropertyValues().get("basePackage");
      if (existingPackages instanceof String packageList) {
        for (String packageName : StringUtils.commaDelimitedListToStringArray(packageList)) {
          if (StringUtils.hasText(packageName)) {
            mergedPackages.add(packageName.trim());
          }
        }
      }
      mergedPackages.addAll(packages);
      scanner.getPropertyValues().add("basePackage", String.join(",", mergedPackages));
      return;
    }

    BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
    builder.addPropertyValue("processPropertyPlaceHolders", true);
    builder.addPropertyValue("annotationClass", RegisteredMapper.class);
    builder.addPropertyValue("basePackage", String.join(",", packages));
    builder.addPropertyValue("sqlSessionFactoryBeanName", "sqlSessionFactory");
    builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    registry.registerBeanDefinition(scannerBeanName, builder.getBeanDefinition());
  }
}
