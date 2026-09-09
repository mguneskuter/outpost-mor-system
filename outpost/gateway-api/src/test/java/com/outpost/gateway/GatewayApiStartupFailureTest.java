package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

class GatewayApiStartupFailureTest {

  @Test
  void failsWhenRequiredDatasourcePropertyIsAbsent() {
    assertThatThrownBy(() -> start(/* url= */ null)).isInstanceOf(Exception.class);
  }

  @Test
  void failsClosedWhenDatabaseIsUnavailable() {
    assertThatThrownBy(() -> start(/* url= */ "jdbc:postgresql://127.0.0.1:1/outpost"))
        .isInstanceOf(Exception.class);
  }

  private void start(@Nullable String url) {
    MockEnvironment env = new MockEnvironment();
    if (url != null) {
      env.setProperty("spring.datasource.url", url);
      env.setProperty("spring.datasource.username", "outpost");
      env.setProperty("spring.datasource.password", "outpost");
      env.setProperty("spring.datasource.hikari.connection-timeout", "2000");
    }
    new SpringApplicationBuilder(GatewayApiApplication.class)
        .web(WebApplicationType.NONE)
        .environment(env)
        .run();
  }
}
