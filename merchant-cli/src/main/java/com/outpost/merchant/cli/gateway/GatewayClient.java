package com.outpost.merchant.cli.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.merchant.cli.configuration.MerchantCliProperties.MerchantCredentials;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Sends the merchant's and the operator's requests to the Gateway. A merchant request carries the
 * merchant's API key and the HMAC-SHA-256 signature of the exact body it sends; the operator's
 * carries only its key.
 */
public final class GatewayClient {
  private static final byte[] NO_BODY = new byte[0];
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final URI baseUrl;
  private final HttpClient http;
  private final ObjectMapper json;

  /** Creates a client for the Gateway at {@code baseUrl}. */
  public GatewayClient(URI baseUrl, HttpClient http, ObjectMapper json) {
    this.baseUrl = baseUrl;
    this.http = http;
    this.json = json;
  }

  /** Creates an order and answers its payment link. */
  public CreatedOrder createOrder(MerchantCredentials merchant, CreateOrderRequest request) {
    return send(
        merchantRequest(merchant, "POST", "/v1/order", json.writeValueAsBytes(request)),
        CreatedOrder.class);
  }

  /** Refunds the whole order and answers the refund's reference. */
  public String refund(MerchantCredentials merchant, String orderReference, String idempotencyKey) {
    byte[] body =
        json.writeValueAsBytes(
            new RefundRequest(orderReference, idempotencyKey, "refund-of-" + orderReference));
    String refundReference =
        send(merchantRequest(merchant, "POST", "/v1/order/modification", body), Refund.class)
            .refundReference();
    if (refundReference == null) {
      throw new GatewayException(200, "REFUND_REFERENCE_MISSING");
    }
    return refundReference;
  }

  /** What Outpost owes the merchant. */
  public BalanceReport merchantBalances(MerchantCredentials merchant) {
    return send(
        merchantRequest(merchant, "GET", "/v1/report/balance/merchant", NO_BODY),
        BalanceReport.class);
  }

  /** What Outpost owes each tax authority; the operator's view. */
  public BalanceReport taxBalances(String operatorApiKey) {
    HttpRequest request =
        HttpRequest.newBuilder(baseUrl.resolve("/v1/report/balance/tax"))
            .timeout(TIMEOUT)
            .header("X-Outpost-Api-Key", operatorApiKey)
            .GET()
            .build();
    return send(request, BalanceReport.class);
  }

  private HttpRequest merchantRequest(
      MerchantCredentials merchant, String method, String path, byte[] body) {
    return HttpRequest.newBuilder(baseUrl.resolve(path))
        .timeout(TIMEOUT)
        .header("Content-Type", "application/json")
        .header("X-Outpost-Api-Key", merchant.apiKey())
        .header("X-Outpost-Signature", HmacSigner.sign(merchant.hmacSecret(), body))
        .method(method, BodyPublishers.ofByteArray(body))
        .build();
  }

  private <T> T send(HttpRequest request, Class<T> responseType) {
    HttpResponse<byte[]> response;
    try {
      response = http.send(request, BodyHandlers.ofByteArray());
    } catch (IOException exception) {
      throw new GatewayException(0, "GATEWAY_UNREACHABLE");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new GatewayException(0, "INTERRUPTED");
    }
    if (response.statusCode() >= 400) {
      throw new GatewayException(response.statusCode(), errorCode(response.body()));
    }
    return json.readValue(response.body(), responseType);
  }

  private String errorCode(byte[] body) {
    String text = new String(body, StandardCharsets.UTF_8);
    try {
      JsonNode code = json.readTree(body).get("code");
      return code == null ? text : code.asString();
    } catch (RuntimeException notJson) {
      return text;
    }
  }

  private record RefundRequest(
      @JsonProperty("order_reference") String orderReference,
      @JsonProperty("idempotency_key") String idempotencyKey,
      @JsonProperty("merchant_reference") String merchantReference,
      @JsonProperty("type") String type) {
    private RefundRequest(String orderReference, String idempotencyKey, String merchantReference) {
      this(orderReference, idempotencyKey, merchantReference, "REFUND");
    }
  }

  private record Refund(@JsonProperty("refund_reference") @Nullable String refundReference) {}
}
