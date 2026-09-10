package com.outpost.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FrameworkFreeDomainModelsTest {
  private static final List<String> DOMAIN_PACKAGES =
      List.of(
          "com.outpost.platform.staticdata",
          "com.outpost.common.iso",
          "com.outpost.payment.common",
          "com.outpost.account",
          "com.outpost.account.configuration",
          "com.outpost.accounting",
          "com.outpost.tax");

  private static final List<String> ALLOWED_PACKAGES =
      List.of(
          "java",
          "org.jspecify",
          "com.outpost.platform.staticdata",
          "com.outpost.common.iso",
          "com.outpost.payment.common",
          "com.outpost.account",
          "com.outpost.account.configuration",
          "com.outpost.accounting",
          "com.outpost.tax",
          "com.outpost.fx",
          "com.outpost.payment",
          "com.outpost.psp");

  @Test
  void domainModelsDoNotDependOnFrameworkOrPersistenceTypes() {
    JavaClasses domainClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(DOMAIN_PACKAGES);

    classes()
        .that()
        .resideInAnyPackage(domainPackagePatterns())
        .should(new FrameworkFreeCondition())
        .check(domainClasses);
  }

  private static boolean isAllowedPackage(String packageName) {
    for (String allowedPackage : ALLOWED_PACKAGES) {
      if (packageName.equals(allowedPackage) || packageName.startsWith(allowedPackage + ".")) {
        return true;
      }
    }
    return false;
  }

  private static final class FrameworkFreeCondition extends ArchCondition<JavaClass> {
    private FrameworkFreeCondition() {
      super("only depend on JDK, annotations, and domain packages");
    }

    @Override
    public void check(JavaClass item, ConditionEvents events) {
      for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
        String targetPackage = dependency.getTargetClass().getPackageName();
        if (!isAllowedPackage(targetPackage)) {
          String sourceName = item.getName();
          String targetName = dependency.getTargetClass().getName();
          String message = sourceName + " depends on " + targetName;
          events.add(SimpleConditionEvent.violated(item, message));
        }
      }
    }
  }

  private static String[] domainPackagePatterns() {
    return packagePatterns(DOMAIN_PACKAGES);
  }

  private static String[] packagePatterns(List<String> packages) {
    return Stream.concat(
            packages.stream(), packages.stream().map(packageName -> packageName + ".."))
        .toArray(String[]::new);
  }
}
