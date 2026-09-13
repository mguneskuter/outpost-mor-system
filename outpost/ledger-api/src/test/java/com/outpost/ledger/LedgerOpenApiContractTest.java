package com.outpost.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.ledger.accountingrequest.api.AccountingRequestController;
import com.outpost.ledger.report.api.BalanceReportController;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * The published OpenAPI document names exactly the routes the controllers serve through the shared
 * {@code accounting:api} contract.
 */
class LedgerOpenApiContractTest {
  private static final List<Class<?>> CONTROLLERS =
      List.of(AccountingRequestController.class, BalanceReportController.class);

  @Test
  void documentsEveryContractRouteAndNothingElse() {
    assertThat(documentedOperations("../../openapi/ledger-api.yaml"))
        .containsExactlyElementsOf(contractOperations());
  }

  private static Set<String> contractOperations() {
    Set<String> operations = new TreeSet<>();
    for (Class<?> controller : CONTROLLERS) {
      HttpExchange root =
          AnnotatedElementUtils.findMergedAnnotation(controller, HttpExchange.class);
      String prefix = root == null ? "" : root.value();
      for (Method method : controller.getDeclaredMethods()) {
        HttpExchange exchange =
            AnnotatedElementUtils.findMergedAnnotation(method, HttpExchange.class);
        if (exchange == null) {
          continue;
        }
        operations.add(
            exchange.method().toLowerCase(Locale.ROOT) + " " + prefix + exchange.value());
      }
    }
    return operations;
  }

  private static Set<String> documentedOperations(String document) {
    YamlMapFactoryBean yaml = new YamlMapFactoryBean();
    yaml.setResources(new FileSystemResource(document));
    Map<String, Object> root = Objects.requireNonNull(yaml.getObject());
    Set<String> operations = new TreeSet<>();
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> paths =
        (Map<String, Map<String, Object>>) Objects.requireNonNull(root.get("paths"));
    paths.forEach((path, item) -> item.keySet().forEach(verb -> operations.add(verb + " " + path)));
    return operations;
  }
}
