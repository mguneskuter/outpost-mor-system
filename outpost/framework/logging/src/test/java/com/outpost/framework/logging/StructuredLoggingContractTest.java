package com.outpost.framework.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class StructuredLoggingContractTest {

  @Test
  void testOnlyFieldExtensionUsesStableOwnerKeys() {
    assertThat(TestFields.APPLICATION.getJsonKey()).isEqualTo("application");
    assertThat(TestFields.REQUEST_ID.getJsonKey()).isEqualTo("request_id");
    assertThat(
            Arrays.stream(StructuredLoggingContractTest.class.getDeclaredClasses())
                .filter(Class::isEnum)
                .map(Class::getName)
                .toList())
        .containsExactly(TestFields.class.getName());
  }

  @Test
  void contextStoresTypedFieldAndRemovesItOnClose() {
    try (StructuredLogContext.Scope ignored =
        StructuredLogContext.open(TestFields.REQUEST_ID, "request-123")) {
      assertThat(MDC.get("request_id")).isEqualTo("request-123");
    }
    assertThat(MDC.get("request_id")).isNull();
  }

  @Test
  void nestedContextRestoresOuterValue() {
    try (StructuredLogContext.Scope outer =
        StructuredLogContext.open(TestFields.REQUEST_ID, "outer-request")) {
      try (StructuredLogContext.Scope inner =
          StructuredLogContext.open(TestFields.REQUEST_ID, "inner-request")) {
        assertThat(MDC.get("request_id")).isEqualTo("inner-request");
      }
      assertThat(MDC.get("request_id")).isEqualTo("outer-request");
    }
    assertThat(MDC.get("request_id")).isNull();
  }

  @Test
  void contextRejectsBlankOwnerKey() {
    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () -> StructuredLogContext.open(() -> " ", "safe")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void loggingApiAcceptsThrowablesButNoArbitraryMapConstructionPath() {
    assertThat(
            Arrays.stream(StructuredLogger.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .anyMatch(Throwable.class::isAssignableFrom))
        .isTrue();
    assertThat(
            Arrays.stream(StructuredLogger.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(java.util.Map.class::isAssignableFrom))
        .isTrue();
  }

  private enum TestFields implements LogFields {
    APPLICATION("application"),
    REQUEST_ID("request_id");

    private final String jsonKey;

    TestFields(String jsonKey) {
      this.jsonKey = jsonKey;
    }

    @Override
    public String getJsonKey() {
      return jsonKey;
    }
  }
}
