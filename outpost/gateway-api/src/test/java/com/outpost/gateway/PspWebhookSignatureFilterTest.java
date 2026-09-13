package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.outpost.gateway.psp.api.PspWebhookSignatureFilter;
import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class PspWebhookSignatureFilterTest {
  private static final String REJECTION_LOGGER = "com.outpost.gateway.psp.api.PspWebhookResponses";
  private static final String PAYLOAD = "shopper@example.test";

  @Test
  void rejectsUnknownPspWithOneWarningThatOmitsPayloadAndSignature() throws Exception {
    PspConfigurationRepository configurations = mock(PspConfigurationRepository.class);
    when(configurations.findByCode("unknown")).thenReturn(Optional.empty());
    ListAppender<ILoggingEvent> appender = appender();

    try {
      MockHttpServletResponse response = filter(configurations, webhook("unknown", "hmac-secret"));

      assertThat(response.getStatus()).isEqualTo(404);
      assertThat(
              new ObjectMapper().readTree(response.getContentAsByteArray()).get("code").asString())
          .isEqualTo("UNKNOWN_PSP");
      assertThat(appender.list).hasSize(1);
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getMDCPropertyMap())
          .containsEntry("rejection_reason", "UNKNOWN_PSP")
          .doesNotContainValue("hmac-secret")
          .doesNotContainValue(PAYLOAD);
    } finally {
      detach(appender);
    }
  }

  @Test
  void rejectsInvalidSignatureBeforeDispatch() throws Exception {
    PspConfigurationRepository configurations = mock(PspConfigurationRepository.class);
    when(configurations.findByCode("PSP"))
        .thenReturn(
            Optional.of(new PspConfiguration(1L, "PSP", "http://psp", "key", "secret", 1, 1)));
    AtomicBoolean dispatched = new AtomicBoolean();

    MockHttpServletResponse response = new MockHttpServletResponse();
    new PspWebhookSignatureFilter(configurations, new ObjectMapper())
        .doFilter(
            webhook("PSP", "bm90LWEtc2lnbmF0dXJl"), response, (req, res) -> dispatched.set(true));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(new ObjectMapper().readTree(response.getContentAsByteArray()).get("code").asString())
        .isEqualTo("INVALID_SIGNATURE");
    assertThat(dispatched).isFalse();
  }

  private static MockHttpServletResponse filter(
      PspConfigurationRepository configurations, MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    new PspWebhookSignatureFilter(configurations, new ObjectMapper())
        .doFilter(request, response, (req, res) -> {});
    return response;
  }

  private static MockHttpServletRequest webhook(String pspCode, String signature) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/v1/psp/" + pspCode + "/webhook");
    request.addHeader("X-Outpost-Signature", signature);
    request.setContent(PAYLOAD.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private static ListAppender<ILoggingEvent> appender() {
    Logger logger = (Logger) LoggerFactory.getLogger(REJECTION_LOGGER);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static void detach(ListAppender<ILoggingEvent> appender) {
    Logger logger = (Logger) LoggerFactory.getLogger(REJECTION_LOGGER);
    logger.detachAppender(appender);
    appender.stop();
  }
}
