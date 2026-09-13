package com.outpost.gateway.psp.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.outpost.gateway.psp.service.PspWebhookProcessResultCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

class PspWebhookResponseCodesTest {
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
  void logsEachRejectionOnceUnderItsOwnReason(PspWebhookProcessResultCodes reason) {
    ResponseEntity<PspWebhookEventResponse> response = PspWebhookResponseCodes.respond(reason);

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
    assertThat(PspWebhookResponseCodes.respond(reason).getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void leavesAnEventTheFullQueueRefusedUnacknowledgedSoThePspRedeliversIt() {
    assertThat(
            PspWebhookResponseCodes.respond(PspWebhookProcessResultCodes.QUEUE_FULL)
                .getStatusCode()
                .value())
        .isEqualTo(503);
  }

  @Test
  void doesNotLogAnAcceptedEvent() {
    PspWebhookResponseCodes.respond(PspWebhookProcessResultCodes.ACCEPTED);

    assertThat(appender.list).isEmpty();
  }

  private static Logger logger() {
    return (Logger) LoggerFactory.getLogger(PspWebhookResponseCodes.class);
  }
}
