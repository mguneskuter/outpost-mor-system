package com.outpost.integration.psp.simulator;

import static com.outpost.integration.psp.PspResultCodes.ACCEPTED;
import static com.outpost.integration.psp.PspResultCodes.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.common.iso.Currencies;
import com.outpost.integration.psp.CreatePspOrderRequest;
import com.outpost.integration.psp.CreatePspOrderResult;
import com.outpost.integration.psp.RefundPspOrderLine;
import com.outpost.integration.psp.RefundPspOrderRequest;
import com.outpost.integration.psp.RefundPspOrderResult;
import com.outpost.integration.psp.UnknownPspResultException;
import com.outpost.payment.common.Amount;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimulatorPspClientTest {
  private HttpServer server;
  private final List<String> requests = new ArrayList<>();
  private SimulatorPspClient client;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", this::handle);
    server.setExecutor(Executors.newSingleThreadExecutor());
    server.start();
    client = new SimulatorPspClient(code -> Optional.of(configuration(1000)));
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void sendsCreateAndRefundRequestsAndMapsResponses() {
    var create = client.createOrder(new CreatePspOrderRequest("DEMO_PSP", "pay-1", amount(10)));
    var refund = client.refund(refundRequest());

    assertThat(create).isEqualTo(new CreatePspOrderResult("psp-1", "https://pay", ACCEPTED));
    assertThat(refund).isEqualTo(new RefundPspOrderResult("psp-1", "refund-1", ACCEPTED));
    assertThat(requests)
        .containsExactly(
            "/v1/DEMO_PSP/order|{\"payment_reference\":\"pay-1\",\"amount\":10,"
                + "\"currency\":\"EUR\"}|secret",
            "/v1/DEMO_PSP/refund|{\"psp_reference\":\"psp-1\",\"payment_reference\":\"pay-1\","
                + "\"refund_reference\":\"refund-1\",\"amount\":9680,\"currency\":\"EUR\","
                + "\"refund_lines\":[{\"order_line_reference\":\"line-1\",\"tax_rate\":\"0.21\","
                + "\"net_amount\":8000,\"gross_amount\":9680}]}|secret");
  }

  @Test
  void mapsRejectedRefundResponse() {
    answerEveryCall(200, "{\"psp_refund_reference\":\"refund-1\",\"accepted\":false}");

    var result = client.refund(refundRequest());

    assertThat(result).isEqualTo(new RefundPspOrderResult("psp-1", "refund-1", REJECTED));
  }

  @Test
  void mapsCallTheSimulatorRefusesToRejected() {
    answerEveryCall(422, "{}");

    var result = client.createOrder(new CreatePspOrderRequest("DEMO_PSP", "pay-1", amount(10)));

    assertThat(result.resultCode()).isEqualTo(REJECTED);
  }

  @Test
  void reportsServerErrorAsUnknownResult() {
    answerEveryCall(500, "{}");

    assertThatThrownBy(() -> client.refund(refundRequest()))
        .isInstanceOf(UnknownPspResultException.class);
  }

  @Test
  void reportsTimeoutAsUnknownResultWithoutRetrying() {
    CountDownLatch released = new CountDownLatch(1);
    server.removeContext("/");
    server.createContext(
        "/",
        exchange -> {
          try {
            released.await();
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
        });
    client = new SimulatorPspClient(code -> Optional.of(configuration(50)));

    try {
      assertThatThrownBy(
              () -> client.createOrder(new CreatePspOrderRequest("DEMO_PSP", "pay-1", amount(10))))
          .isInstanceOf(UnknownPspResultException.class);
    } finally {
      released.countDown();
    }
    assertThat(requests).isEmpty();
  }

  private RefundPspOrderRequest refundRequest() {
    return new RefundPspOrderRequest(
        "DEMO_PSP",
        "psp-1",
        "pay-1",
        "refund-1",
        amount(9680),
        List.of(
            new RefundPspOrderLine("line-1", new BigDecimal("0.21"), amount(8000), amount(9680))));
  }

  private void answerEveryCall(int status, String body) {
    server.removeContext("/");
    server.createContext("/", exchange -> respond(exchange, status, body));
  }

  private void handle(HttpExchange exchange) throws IOException {
    requests.add(
        exchange.getRequestURI()
            + "|"
            + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            + "|"
            + exchange.getRequestHeaders().getFirst("X-Outpost-Api-Key"));
    if (exchange.getRequestURI().getPath().endsWith("/order")) {
      respond(exchange, 201, "{\"psp_reference\":\"psp-1\",\"payment_url\":\"https://pay\"}");
    } else {
      respond(exchange, 200, "{\"psp_refund_reference\":\"refund-1\",\"accepted\":true}");
    }
  }

  private void respond(HttpExchange exchange, int status, String response) throws IOException {
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private PspConfiguration configuration(int readTimeoutMillis) {
    return new PspConfiguration(
        300, "DEMO_PSP", baseUrl(), "secret", "hmac", 1000, readTimeoutMillis);
  }

  private Amount amount(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
  }
}
