package com.outpost.gateway.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GatewayArchitectureTest {
  private static final JavaClasses GATEWAY_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.outpost.gateway");

  @Test
  void productionClassesStayUnderTheGatewayPackage() {
    classes().should().resideInAPackage("com.outpost.gateway..").check(GATEWAY_CLASSES);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"clean", "hexagonal", "port", "adapter", "inbound", "outbound", "impl", "util"})
  void noPackageUsesForbiddenLayerName(String forbiddenName) {
    noClasses().should().resideInAPackage("..%s..".formatted(forbiddenName)).check(GATEWAY_CLASSES);
  }

  @Test
  void nothingDependsOnLedger() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.outpost.ledger..")
        .check(GATEWAY_CLASSES);
  }
}
