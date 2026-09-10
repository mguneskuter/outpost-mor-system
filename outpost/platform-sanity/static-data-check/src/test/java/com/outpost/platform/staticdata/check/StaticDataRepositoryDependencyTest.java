package com.outpost.platform.staticdata.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.platform.staticdata.StaticDataRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class StaticDataRepositoryDependencyTest {

  @Test
  void everyAnnotatedEnumHasExactlyOneRealRepositoryImplementation() {
    List<StaticDataRepository<?, ?, ?>> repositories = repositoryImplementations();
    Set<Class<?>> claimedEnums =
        repositories.stream().map(StaticDataRepository::staticDataEnum).collect(Collectors.toSet());

    List<Class<?>> annotatedEnums = annotatedEnums();
    assertThat(repositories).hasSize(annotatedEnums.size());
    assertThat(claimedEnums).containsExactlyInAnyOrderElementsOf(annotatedEnums);
    assertThat(claimedEnums).hasSameSizeAs(repositories);
    assertThat(repositories)
        .allSatisfy(
            repository ->
                assertThat(
                        repository.getClass().getPackageName().endsWith(".repository")
                            || repository
                                .getClass()
                                .getPackageName()
                                .endsWith(".repository.sanity"))
                    .as("repository package should be a repository package")
                    .isTrue());
  }

  private static List<StaticDataRepository<?, ?, ?>> repositoryImplementations() {
    JavaClasses imported =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.outpost");
    return StreamSupport.stream(imported.spliterator(), false)
        .map(javaClass -> (Class<?>) javaClass.reflect())
        .filter(
            type ->
                StaticDataRepository.class.isAssignableFrom(type)
                    && !type.isInterface()
                    && !java.lang.reflect.Modifier.isAbstract(type.getModifiers()))
        .map(StaticDataRepositoryDependencyTest::instantiate)
        .toList();
  }

  private static List<Class<?>> annotatedEnums() {
    JavaClasses imported =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.outpost");
    List<Class<?>> result = new ArrayList<>();
    imported.forEach(
        javaClass -> {
          Class<?> type = javaClass.reflect();
          if (type.isEnum()
              && type.isAnnotationPresent(com.outpost.platform.staticdata.StaticData.class)) {
            result.add(type);
          }
        });
    return result;
  }

  private static StaticDataRepository<?, ?, ?> instantiate(Class<?> repositoryType) {
    try {
      Constructor<?> constructor = repositoryType.getDeclaredConstructors()[0];
      constructor.setAccessible(true);
      Class<?> mapperType = constructor.getParameterTypes()[0];
      Object mapper =
          Proxy.newProxyInstance(
              mapperType.getClassLoader(),
              new Class<?>[] {mapperType},
              (proxy, method, args) -> method.getReturnType() == List.class ? List.of() : null);
      return (StaticDataRepository<?, ?, ?>) constructor.newInstance(mapper);
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError("Could not instantiate " + repositoryType.getName(), exception);
    }
  }
}
