package com.outpost.integration.psp.simulator;

import static com.outpost.integration.psp.service.ResultCode.ACCEPTED;
import static com.outpost.integration.psp.service.ResultCode.REJECTED;
import static com.outpost.integration.psp.service.ResultCode.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.common.iso.Currencies;
import com.outpost.integration.psp.service.CancelRequest;
import com.outpost.integration.psp.service.CancelResult;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.CreateOrderResult;
import com.outpost.integration.psp.service.RefundRequest;
import com.outpost.integration.psp.service.RefundResult;
import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import com.outpost.payment.common.Amount;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    client = new SimulatorPspClient(code -> Optional.of(configuration()));
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void sendsCreateRefundAndCancelRequestsAndMapsResponses() {
    var create = client.createOrder(new CreateOrderRequest("DEMO_PSP", "pay-1", amount(10)));
    var refund = client.refund(new RefundRequest("DEMO_PSP", "psp-1", "refund-1", amount(1)));
    var cancel = client.cancel(new CancelRequest("DEMO_PSP", "psp-1"));

    assertThat(create).isEqualTo(new CreateOrderResult("psp-1", "https://pay", ACCEPTED));
    assertThat(refund).isEqualTo(new RefundResult("psp-1", "refund-1", ACCEPTED));
    assertThat(cancel).isEqualTo(new CancelResult("psp-1", ACCEPTED));
    assertThat(requests)
        .containsExactly(
            "/v1/DEMO_PSP/order|{\"payment_reference\":\"pay-1\",\"amount\":10,"
                + "\"currency\":\"EUR\"}|secret",
            "/v1/DEMO_PSP/refund|{\"psp_reference\":\"psp-1\",\"refund_reference\":\"refund-1\","
                + "\"amount\":1,\"currency\":\"EUR\"}|secret",
            "/v1/DEMO_PSP/cancel|{\"psp_reference\":\"psp-1\"}|secret");
  }

  @Test
  void mapsRejectedRefundResponse() {
    server.removeContext("/");
    server.createContext(
        "/",
        exchange ->
            respond(exchange, 200, "{\"psp_refund_reference\":\"refund-1\",\"accepted\":false}"));

    var result = client.refund(new RefundRequest("DEMO_PSP", "psp-1", "refund-1", amount(1)));

    assertThat(result).isEqualTo(new RefundResult("psp-1", "refund-1", REJECTED));
  }

  @Test
  void reportsTimeoutAsUnknownWithoutRetrying() throws IOException {
    server.removeContext("/");
    server.createContext(
        "/",
        exchange -> {
          try {
            Thread.sleep(250);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    client =
        new SimulatorPspClient(
            code ->
                Optional.of(
                    new PspConfiguration(300, "DEMO_PSP", baseUrl(), "secret", "hmac", 1000, 50)));

    var result = client.createOrder(new CreateOrderRequest("DEMO_PSP", "pay-1", amount(10)));

    assertThat(result.resultCode()).isEqualTo(UNKNOWN);
    assertThat(requests).isEmpty();
  }

  private void handle(HttpExchange exchange) throws IOException {
    requests.add(
        exchange.getRequestURI()
            + "|"
            + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            + "|"
            + exchange.getRequestHeaders().getFirst("X-Outpost-Api-Key"));
    if (exchange.getRequestURI().getPath().endsWith("/cancel")) {
      respond(exchange, 202, "");
    } else if (exchange.getRequestURI().getPath().endsWith("/order")) {
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

  private PspConfiguration configuration() {
    return new PspConfiguration(300, "DEMO_PSP", baseUrl(), "secret", "hmac", 1000, 1000);
  }

  private Amount amount(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
  }
}
