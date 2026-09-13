package com.outpost.gateway.psp.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.outpost.gateway.psp.service.PspWebhookProcessResultCodes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

class PspWebhookResponsesTest {
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final PspWebhookResponses responses = new PspWebhookResponses(meterRegistry);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void attachLogAppender() {
    appender.start();
    logger().addAppender(appender);
  }

  @AfterEach
  void detachLogAppender() {
    logger().detachAppender(appender);
    appender.stop();
  }

  @ParameterizedTest
  @EnumSource(
      value = PspWebhookProcessResultCodes.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = "ACCEPTED")
  void countsAndLogsEachRejectionOnceUnderItsOwnReason(PspWebhookProcessResultCodes reason) {
    ResponseEntity<PspWebhookEventResponse> response = responses.respond(reason);

    assertThat(meterRegistry.find("outpost.webhook.rejected").counters())
        .singleElement()
        .satisfies(
            counter -> {
              assertThat(counter.getId().getTag("reason")).isEqualTo(reason.name());
              assertThat(counter.count()).isEqualTo(1.0);
            });
    assertThat(appender.list)
        .singleElement()
        .extracting(ILoggingEvent::getLevel)
        .isEqualTo(Level.WARN);
    assertThat(appender.list.getFirst().getMDCPropertyMap())
        .containsOnlyKeys("rejection_reason")
        .containsEntry("rejection_reason", reason.name());
    assertThat(response.getBody()).isEqualTo(new PspWebhookEventResponse(reason.name()));
  }

  @ParameterizedTest
  @EnumSource(
      value = PspWebhookProcessResultCodes.class,
      names = {"UNKNOWN_PAYMENT", "FOREIGN_PAYMENT", "PSP_REFERENCE_MISMATCH"})
  void acknowledgesAuthenticatedEventThatMatchesNoStoredPayment(
      PspWebhookProcessResultCodes reason) {
    assertThat(responses.respond(reason).getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void neitherCountsNorLogsAnAcceptedEvent() {
    responses.respond(PspWebhookProcessResultCodes.ACCEPTED);

    assertThat(meterRegistry.find("outpost.webhook.rejected").counters())
        .extracting(Counter::count)
        .allMatch(count -> count == 0.0);
    assertThat(appender.list).isEmpty();
  }

  private static Logger logger() {
    return (Logger) LoggerFactory.getLogger(PspWebhookResponses.class);
  }
}
