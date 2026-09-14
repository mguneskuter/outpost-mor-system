package com.outpost.merchant.cli.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import java.time.LocalDate;
import java.util.List;
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

  /**
   * Refunds the named order lines, or every line not yet refunded when none is named, and answers
   * the refund's reference.
   */
  public String refund(
      MerchantCredentials merchant,
      String orderReference,
      String idempotencyKey,
      List<String> orderLineReferences) {
    byte[] body =
        json.writeValueAsBytes(
            new RefundRequest(
                orderReference,
                idempotencyKey,
                "refund-of-" + orderReference,
                "REFUND",
                orderLineReferences.isEmpty() ? null : orderLineReferences));
    String refundReference =
        send(merchantRequest(merchant, "POST", "/v1/order/modification", body), Refund.class)
            .refundReference();
    if (refundReference == null) {
      throw new GatewayException(200, "REFUND_REFERENCE_MISSING");
    }
    return refundReference;
  }

  /** Builds the merchant's balance report over the period and answers the URL it is read at. */
  public String requestMerchantReport(MerchantCredentials merchant, LocalDate from, LocalDate to) {
    return reportUrl(
        send(merchantRequest(merchant, "GET", reportPath(from, to), NO_BODY), ReportLink.class));
  }

  /** Builds the platform's balance report over the period and answers the URL it is read at. */
  public String requestPlatformReport(String operatorApiKey, LocalDate from, LocalDate to) {
    return reportUrl(
        send(
            operatorRequest(operatorApiKey, baseUrl.resolve(reportPath(from, to))),
            ReportLink.class));
  }

  /** Reads a built report at its URL as the merchant. */
  public BalanceReport readMerchantReport(MerchantCredentials merchant, String reportUrl) {
    return send(
        merchantRequest(merchant, "GET", URI.create(reportUrl), NO_BODY), BalanceReport.class);
  }

  /** Reads a built report at its URL as the operator. */
  public BalanceReport readPlatformReport(String operatorApiKey, String reportUrl) {
    return send(operatorRequest(operatorApiKey, URI.create(reportUrl)), BalanceReport.class);
  }

  private static String reportPath(LocalDate from, LocalDate to) {
    return "/v1/report?from=" + from + "&to=" + to;
  }

  private static String reportUrl(ReportLink link) {
    if (link.reportUrl() == null) {
      throw new GatewayException(200, "REPORT_URL_MISSING");
    }
    return link.reportUrl();
  }

  private HttpRequest operatorRequest(String operatorApiKey, URI uri) {
    return HttpRequest.newBuilder(uri)
        .timeout(TIMEOUT)
        .header("X-Outpost-Api-Key", operatorApiKey)
        .GET()
        .build();
  }

  private HttpRequest merchantRequest(
      MerchantCredentials merchant, String method, String path, byte[] body) {
    return merchantRequest(merchant, method, baseUrl.resolve(path), body);
  }

  private HttpRequest merchantRequest(
      MerchantCredentials merchant, String method, URI uri, byte[] body) {
    return HttpRequest.newBuilder(uri)
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
      @JsonProperty("type") String type,
      @JsonProperty("order_line_references") @JsonInclude(JsonInclude.Include.NON_NULL)
          @Nullable List<String> orderLineReferences) {}

  private record Refund(@JsonProperty("refund_reference") @Nullable String refundReference) {}

  private record ReportLink(@JsonProperty("report_url") @Nullable String reportUrl) {}
}
