package com.outpost.platform.staticdata.check;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

class StaticDataRulesTest {

  @Test
  void staticDataAnnotationIsRuntimeVisibleAndOnlyTargetsTypes() {
    assertThat(
            com.outpost.platform.staticdata.StaticData.class.getAnnotation(Retention.class).value())
        .isEqualTo(RetentionPolicy.RUNTIME);
    assertThat(com.outpost.platform.staticdata.StaticData.class.getAnnotation(Target.class).value())
        .containsExactly(ElementType.TYPE);
  }

  @Test
  void staticDataEnumsAreAnnotatedAndRepositoryAdaptersStayInRepositoryPackages() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.outpost");

    ArchRule annotatedEnums =
        classes()
            .that()
            .areAnnotatedWith(com.outpost.platform.staticdata.StaticData.class)
            .should()
            .beEnums();
    annotatedEnums.check(classes);

    ArchRule repositoriesInAdapters =
        classes()
            .that()
            .implement(com.outpost.platform.staticdata.StaticDataRepository.class)
            .should()
            .resideInAnyPackage("..repository..");
    repositoriesInAdapters.check(classes);

    ArchRule annotatedTypesAreEnums =
        classes()
            .that()
            .areAnnotatedWith(com.outpost.platform.staticdata.StaticData.class)
            .should()
            .beEnums();
    assertThatThrownBy(
            () ->
                annotatedTypesAreEnums.check(
                    new ClassFileImporter().importClasses(InvalidStaticDataType.class)))
        .isInstanceOf(AssertionError.class);
  }

  @com.outpost.platform.staticdata.StaticData
  private static final class InvalidStaticDataType {}
}
