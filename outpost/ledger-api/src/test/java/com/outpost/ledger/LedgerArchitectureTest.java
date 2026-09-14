package com.outpost.ledger;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.outpost.accounting.journalentry.JournalEntry;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LedgerArchitectureTest {

  private static final JavaClasses LEDGER_CLASSES =
      new ClassFileImporter().importPath(productionClassesPath());

  private static final JavaClasses ACCOUNTING_AND_LEDGER_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.outpost.accounting", "com.outpost.ledger");

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
        .resideOutsideOfPackages("com.outpost.gateway..")
        .check(LEDGER_CLASSES);
  }

  @Test
  void onlyJournalTemplatesPutLinesOnJournalEntries() {
    noClasses()
        .that()
        .resideOutsideOfPackage("com.outpost.accounting.templates")
        .should()
        .accessTargetWhere(accessToJournalEntry("addLine"))
        .check(ACCOUNTING_AND_LEDGER_CLASSES);
  }

  @Test
  void onlyJournalEntryPersistenceAssignsStoredIds() {
    noClasses()
        .that()
        .resideOutsideOfPackage("..repository.mybatis..")
        .should()
        .accessTargetWhere(accessToJournalEntry("withIds"))
        .check(ACCOUNTING_AND_LEDGER_CLASSES);
  }

  @Test
  void publicJournalTemplateTypesAreJournalTemplatesEnums() {
    classes()
        .that()
        .resideInAPackage("com.outpost.accounting.templates")
        .and()
        .arePublic()
        .and()
        .areTopLevelClasses()
        .should()
        .beEnums()
        .andShould()
        .haveSimpleNameEndingWith("JournalTemplates")
        .check(ACCOUNTING_AND_LEDGER_CLASSES);
  }

  @Test
  void journalTemplatesBuildJournalEntriesOnlyThroughBuild() {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("com.outpost.accounting.templates")
        .and()
        .arePublic()
        .and()
        .haveRawReturnType(JournalEntry.class)
        .should()
        .haveName("build")
        .check(ACCOUNTING_AND_LEDGER_CLASSES);
  }

  private static DescribedPredicate<JavaAccess<?>> accessToJournalEntry(String methodName) {
    return DescribedPredicate.describe(
        "call or reference JournalEntry." + methodName,
        access ->
            access.getTargetOwner().isEquivalentTo(JournalEntry.class)
                && access.getTarget().getName().equals(methodName));
  }
}
