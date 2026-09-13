package com.outpost.pspsimulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.outpost.pspsimulator.configuration.SimulatorProperties;
import com.outpost.pspsimulator.order.Order;
import com.outpost.pspsimulator.order.OrderService;
import com.outpost.pspsimulator.order.OrderStatuses;
import com.outpost.pspsimulator.psp.PspAccount;
import com.outpost.pspsimulator.psp.PspAccounts;
import com.outpost.pspsimulator.refund.RefundService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PspApiHttpTest {

  private static final String API_KEY = "api-key";
  private MockMvc mvc;
  private OrderService orders;
  private RefundService refunds;

  @BeforeEach
  void setUp() {
    orders = mock(OrderService.class);
    refunds = mock(RefundService.class);
    SimulatorProperties properties =
        new SimulatorProperties(
            "http://localhost:8083",
            "http://localhost:8080",
            new SimulatorProperties.DelaySettings(
                Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
            List.of(new PspAccount("DEMO_PSP", API_KEY, "secret")));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new PspApiController(new PspAccounts(properties), orders, refunds))
            .setControllerAdvice(new PspSimulatorExceptionHandler())
            .build();
  }

  @Test
  void rejectsWrongApiKeyAndUnknownPspBeforeCallingOperations() throws Exception {
    mvc.perform(
            post("/v1/DEMO_PSP/order")
                .header("X-Outpost-Api-Key", "wrong")
                .contentType("application/json")
                .content(orderJson()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/v1/UNKNOWN/order")
                .header("X-Outpost-Api-Key", API_KEY)
                .contentType("application/json")
                .content(orderJson()))
        .andExpect(status().isNotFound());
  }

  @Test
  void repeatedOrderRequestReturnsTheSameReferenceWithOk() throws Exception {
    Order order = new Order("DEMO_PSP", 41, "payment-1", 1250, "EUR", OrderStatuses.CREATED);
    when(orders.createOrder(any(), any()))
        .thenReturn(new OrderService.CreateOrderResult(order, "http://payment", true))
        .thenReturn(new OrderService.CreateOrderResult(order, "http://payment", false));

    mvc.perform(
            post("/v1/DEMO_PSP/order")
                .header("X-Outpost-Api-Key", API_KEY)
                .contentType("application/json")
                .content(orderJson()))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/v1/DEMO_PSP/order")
                .header("X-Outpost-Api-Key", API_KEY)
                .contentType("application/json")
                .content(orderJson()))
        .andExpect(status().isOk());
  }

  @Test
  void refundResponseEchoesPspReferenceAndRepeatedRequestIsSuccessful() throws Exception {
    when(refunds.refund(any(), any()))
        .thenReturn(new RefundService.RefundResult(77, true))
        .thenReturn(new RefundService.RefundResult(77, true));
    String body =
        """
        {"psp_reference":41,"refund_reference":"refund-1"}
        """;
    mvc.perform(
            post("/v1/DEMO_PSP/refund")
                .header("X-Outpost-Api-Key", API_KEY)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
    var response =
        mvc.perform(
                post("/v1/DEMO_PSP/refund")
                    .header("X-Outpost-Api-Key", API_KEY)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(response.getContentAsString()).contains("\"psp_refund_reference\":\"77\"");
  }

  private static String orderJson() {
    return """
        {"payment_reference":"payment-1","amount":1250,"currency":"EUR"}
        """;
  }
}
