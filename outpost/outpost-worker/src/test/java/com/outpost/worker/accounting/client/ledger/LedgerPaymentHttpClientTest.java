package com.outpost.worker.accounting.client.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.hmac.HmacSignature;
import com.outpost.worker.accounting.client.LedgerPaymentClientException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LedgerPaymentHttpClientTest {
  private static final String SECRET = "worker-secret";

  private HttpServer server;
  private final List<String> paths = new ArrayList<>();
  private final List<String> bodies = new ArrayList<>();
  private LedgerPaymentHttpClient client;
  private int responseStatus = 204;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", this::handle);
    server.setExecutor(Executors.newSingleThreadExecutor());
    server.start();
    client =
        new LedgerPaymentHttpClient(
            "http://localhost:" + server.getAddress().getPort(),
            SECRET,
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            new ObjectMapper());
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void signsAndSendsPaymentEvent() {
    client.recordEvent("payment-1", null, "AUTHORISED");

    assertThat(paths).containsExactly("/v1/payment/event");
    assertSignedBody(
        "{\"payment_reference\":\"payment-1\",\"refund_reference\":null,\"event\":\"AUTHORISED\"}");
  }

  @Test
  void signsAndSendsRefundEventWithItsReference() {
    client.recordEvent("payment-1", "refund-1", "REFUND_ACCEPTED");

    assertSignedBody(
        "{\"payment_reference\":\"payment-1\",\"refund_reference\":\"refund-1\","
            + "\"event\":\"REFUND_ACCEPTED\"}");
  }

  @Test
  void signsAndSendsCapture() {
    client.recordCapture("payment-1", "capture-1", true, 1250, "EUR");

    assertThat(paths).containsExactly("/v1/payment/capture");
    assertSignedBody(
        "{\"payment_reference\":\"payment-1\",\"capture_reference\":\"capture-1\",\"success\":true,"
            + "\"amount\":1250,\"currency\":\"EUR\"}");
  }

  @Test
  void signsAndSendsRefundReservation() {
    client.reserveRefund("payment-1", "refund-1", 800, 152, "EUR");

    assertThat(paths).containsExactly("/v1/payment/refund");
    assertSignedBody(
        "{\"payment_reference\":\"payment-1\",\"refund_reference\":\"refund-1\",\"net_amount\":800,"
            + "\"tax_amount\":152,\"currency\":\"EUR\"}");
  }

  @Test
  void throwsClassifiedExceptionOnFailureResponse() {
    responseStatus = 409;

    assertThatThrownBy(() -> client.recordEvent("payment-1", null, "AUTHORISED"))
        .isInstanceOf(LedgerPaymentClientException.class);
  }

  private void assertSignedBody(String expectedJson) {
    assertThat(bodies).hasSize(1);
    String[] parts = bodies.get(0).split("\\|", 2);
    assertThat(parts[0]).isEqualToIgnoringWhitespace(expectedJson);
    HmacKey key = HmacKey.fromUtf8(SECRET);
    HmacSignature signature = HmacSignature.fromBase64(parts[1]);
    assertThat(HmacSha256.verify(key, parts[0].getBytes(StandardCharsets.UTF_8), signature))
        .isTrue();
  }

  private void handle(HttpExchange exchange) throws IOException {
    byte[] body = exchange.getRequestBody().readAllBytes();
    paths.add(exchange.getRequestURI().getPath());
    bodies.add(
        new String(body, StandardCharsets.UTF_8)
            + "|"
            + exchange.getRequestHeaders().getFirst("X-Outpost-Signature"));
    exchange.sendResponseHeaders(responseStatus, -1);
    exchange.close();
  }
}
