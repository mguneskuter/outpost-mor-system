package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.gateway.order.api.OrderController;
import com.outpost.gateway.order.api.OrderModificationController;
import com.outpost.gateway.psp.api.PspController;
import com.outpost.gateway.psp.api.PspWebhookController;
import com.outpost.gateway.report.api.ReportController;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/** The published OpenAPI document names exactly the routes the controllers serve. */
class GatewayOpenApiContractTest {
  private static final List<Class<?>> CONTROLLERS =
      List.of(
          OrderController.class,
          OrderModificationController.class,
          PspController.class,
          ReportController.class,
          PspWebhookController.class);

  @Test
  void documentsEveryControllerRouteAndNothingElse() {
    assertThat(documentedOperations("../../openapi/gateway-api.yaml"))
        .containsExactlyElementsOf(controllerOperations());
  }

  private static Set<String> controllerOperations() {
    Set<String> operations = new TreeSet<>();
    for (Class<?> controller : CONTROLLERS) {
      RequestMapping root =
          AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
      String prefix = root == null || root.path().length == 0 ? "" : root.path()[0];
      for (Method method : controller.getDeclaredMethods()) {
        RequestMapping mapping =
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
          continue;
        }
        String path = prefix + (mapping.path().length == 0 ? "" : mapping.path()[0]);
        for (RequestMethod verb : mapping.method()) {
          operations.add(verb.name().toLowerCase(Locale.ROOT) + " " + path);
        }
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
