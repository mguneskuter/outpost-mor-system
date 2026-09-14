package com.outpost.pspsimulator.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.outpost.pspsimulator.PspApiController;
import com.outpost.pspsimulator.PspSimulatorExceptionHandler;
import com.outpost.pspsimulator.RecordingWebhookServer;
import com.outpost.pspsimulator.configuration.SimulatorProperties;
import com.outpost.pspsimulator.order.Order;
import com.outpost.pspsimulator.order.OrderRepository;
import com.outpost.pspsimulator.order.OrderService;
import com.outpost.pspsimulator.order.OrderStatuses;
import com.outpost.pspsimulator.order.ResultCodes;
import com.outpost.pspsimulator.psp.PspAccount;
import com.outpost.pspsimulator.psp.PspAccounts;
import com.outpost.pspsimulator.refund.RefundLine;
import com.outpost.pspsimulator.refund.RefundService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class WebhookDispatcherHttpTest {

  @Test
  void approvedAndRefundEventsArriveWithSignedRefundReferenceEcho() throws Exception {
    try (RecordingWebhookServer server = new RecordingWebhookServer()) {
      SimulatorProperties properties =
          new SimulatorProperties(
              "http://localhost:8083",
              "http://localhost:" + server.port(),
              new SimulatorProperties.DelaySettings(
                  Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
              List.of(new PspAccount("DEMO_PSP", "api-key", "secret")));
      WebhookDispatcher dispatcher =
          new WebhookDispatcher(properties, new PspAccounts(properties), new ObjectMapper());
      dispatcher.dispatch(
          payload(WebhookEventCodes.AUTHORISATION, ResultCodes.APPROVED, null, null));
      dispatcher.dispatch(
          payload(
              WebhookEventCodes.REFUND,
              ResultCodes.APPROVED,
              "refund-1",
              List.of(new RefundLine("line-1", "0.21", 1000, 1250))));

      RecordingWebhookServer.WebhookDelivery authorisation =
          java.util.Objects.requireNonNull(server.awaitDelivery(Duration.ofSeconds(2)));
      RecordingWebhookServer.WebhookDelivery refund =
          java.util.Objects.requireNonNull(server.awaitDelivery(Duration.ofSeconds(2)));
      assertThat(authorisation.signature())
          .isEqualTo(new WebhookSigner().signBase64("secret", authorisation.body()));
      assertThat(refund.signature())
          .isEqualTo(new WebhookSigner().signBase64("secret", refund.body()));
      assertThat(refund.bodyText())
          .contains("\"refund_reference\":\"refund-1\"", "\"success\":true")
          .contains(
              "\"refund_lines\":[{\"order_line_reference\":\"line-1\",\"tax_rate\":\"0.21\","
                  + "\"net_amount\":1000,\"gross_amount\":1250}]");
      assertThat(refund.path()).isEqualTo("/v1/psp/DEMO_PSP/webhook");
    }
  }

  @Test
  void refusedPaymentPostsOnlyFailedAuthorisationWebhook() throws Exception {
    try (RecordingWebhookServer server = new RecordingWebhookServer()) {
      SimulatorProperties properties = properties(server);
      Order order = new Order("DEMO_PSP", "psp-1", "payment-1", 1250, "EUR", OrderStatuses.CREATED);
      OrderRepository repository = mock(OrderRepository.class);
      when(repository.findByPspReference("DEMO_PSP", "psp-1"))
          .thenReturn(java.util.Optional.of(order));
      when(repository.transition("DEMO_PSP", "psp-1", OrderStatuses.CREATED, OrderStatuses.REFUSED))
          .thenReturn(true);
      TaskScheduler scheduler = mock(TaskScheduler.class);
      when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
          .thenAnswer(
              invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
              });
      WebhookDispatcher dispatcher =
          new WebhookDispatcher(properties, new PspAccounts(properties), new ObjectMapper());
      OrderService orders =
          new OrderService(
              repository,
              new WebhookScheduler(scheduler, dispatcher, repository, properties),
              properties);
      MockMvc mvc =
          MockMvcBuilders.standaloneSetup(
                  new PspApiController(
                      new PspAccounts(properties), orders, mock(RefundService.class)))
              .setControllerAdvice(new PspSimulatorExceptionHandler())
              .build();

      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/v1/DEMO_PSP/payment")
                  .header("X-Outpost-Api-Key", "api-key")
                  .contentType("application/json")
                  .content("{\"psp_reference\":\"psp-1\",\"card_number\":\"4000000000000002\"}"))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isAccepted());

      RecordingWebhookServer.WebhookDelivery authorisation =
          java.util.Objects.requireNonNull(server.awaitDelivery(Duration.ofSeconds(2)));
      assertThat(authorisation.bodyText())
          .contains("\"event_code\":\"AUTHORISATION\"", "\"result_code\":\"ACQUIRER_REFUSED\"")
          .contains("\"success\":false");
      assertThat(authorisation.signature())
          .isEqualTo(new WebhookSigner().signBase64("secret", authorisation.body()));
      assertThat(server.awaitDelivery(Duration.ofMillis(100))).isNull();
    }
  }

  private static SimulatorProperties properties(RecordingWebhookServer server) {
    return new SimulatorProperties(
        "http://localhost:8083",
        "http://localhost:" + server.port(),
        new SimulatorProperties.DelaySettings(
            Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
        List.of(new PspAccount("DEMO_PSP", "api-key", "secret")));
  }

  private static WebhookPayload payload(
      WebhookEventCodes code,
      ResultCodes result,
      @Nullable String refundReference,
      @Nullable List<RefundLine> refundLines) {
    return new WebhookPayload(
        "DEMO_PSP",
        "psp-1",
        null,
        "payment-1",
        code,
        1,
        result == ResultCodes.APPROVED,
        result,
        1250,
        "EUR",
        refundReference,
        refundLines);
  }
}
