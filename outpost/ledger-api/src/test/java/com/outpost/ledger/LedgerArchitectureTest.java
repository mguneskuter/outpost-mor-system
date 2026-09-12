package com.outpost.ledger;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LedgerArchitectureTest {

  private static final JavaClasses LEDGER_CLASSES =
      new ClassFileImporter().importPath(productionClassesPath());

  private static Path productionClassesPath() {
    try {
      return Path.of(
          LedgerApiApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("Cannot locate Ledger production classes", exception);
    }
  }

  @Test
  void productionClassesStayInLedgerPackage() {
    classes().should().resideInAnyPackage("com.outpost.ledger..").check(LEDGER_CLASSES);
  }

  @Test
  void packageNamesDoNotUseArchitecturalLabels() {
    classes()
        .should()
        .resideOutsideOfPackages(
            "..clean..",
            "..hexagonal..",
            "..port..",
            "..adapter..",
            "..inbound..",
            "..outbound..",
            "..impl..",
            "..util..")
        .check(LEDGER_CLASSES);
  }

  @Test
  void ledgerDoesNotDependOnOtherDeployables() {
    classes()
        .should()
        .onlyDependOnClassesThat()
        .resideOutsideOfPackages("com.outpost.gateway..", "com.outpost.worker..")
        .check(LEDGER_CLASSES);
  }

  @Test
  void myBatisRowsStayInTheirRepositoryPackage() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Row")
        .should()
        .resideInAnyPackage("com.outpost.ledger.payment.repository.mybatis")
        .check(LEDGER_CLASSES);
  }
}
