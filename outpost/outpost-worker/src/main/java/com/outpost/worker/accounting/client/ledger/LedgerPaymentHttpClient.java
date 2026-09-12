package com.outpost.worker.accounting.client.ledger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.worker.accounting.client.LedgerPaymentClient;
import com.outpost.worker.accounting.client.LedgerPaymentClientException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** HTTP adapter for the Ledger payment routes the Worker signs with its own key. */
public final class LedgerPaymentHttpClient implements LedgerPaymentClient {
  private final RestClient client;
  private final HmacKey signingKey;
  private final ObjectMapper objectMapper;

  /** Creates a Ledger payment client with bounded connection and response timeouts. */
  public LedgerPaymentHttpClient(
      String baseUrl,
      String hmacSecret,
      Duration connectTimeout,
      Duration readTimeout,
      ObjectMapper objectMapper) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("Ledger base URL must not be blank");
    }
    if (hmacSecret == null || hmacSecret.isBlank()) {
      throw new IllegalArgumentException("Ledger HMAC secret must not be blank");
    }
    if (connectTimeout.isZero() || connectTimeout.isNegative()) {
      throw new IllegalArgumentException("Ledger connect timeout must be positive");
    }
    if (readTimeout.isZero() || readTimeout.isNegative()) {
      throw new IllegalArgumentException("Ledger read timeout must be positive");
    }
    this.client =
        RestClient.builder()
            .requestFactory(newRequestFactory(connectTimeout, readTimeout))
            .baseUrl(baseUrl)
            .build();
    this.signingKey = HmacKey.fromUtf8(hmacSecret);
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @Override
  public void recordEvent(String paymentReference, @Nullable String refundReference, String event) {
    post("/v1/payment/event", new EventRequest(paymentReference, refundReference, event));
  }

  @Override
  public void recordCapture(
      String paymentReference,
      String captureReference,
      boolean success,
      long amount,
      String currency) {
    post(
        "/v1/payment/capture",
        new CaptureRequest(paymentReference, captureReference, success, amount, currency));
  }

  @Override
  public void reserveRefund(
      String paymentReference,
      String refundReference,
      long netAmount,
      long taxAmount,
      String currency) {
    post(
        "/v1/payment/refund",
        new RefundRequest(paymentReference, refundReference, netAmount, taxAmount, currency));
  }

  private void post(String path, Object request) {
    byte[] body = serialize(request);
    try {
      client
          .post()
          .uri(URI.create(path))
          .header("X-Outpost-Signature", HmacSha256.sign(signingKey, body).toBase64())
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      throw new LedgerPaymentClientException(
          "Ledger call to " + path + " failed with " + exception.getStatusCode(), exception);
    } catch (RuntimeException exception) {
      throw new LedgerPaymentClientException("Ledger call to " + path + " failed", exception);
    }
  }

  private byte[] serialize(Object request) {
    try {
      return objectMapper.writeValueAsBytes(request);
    } catch (JacksonException exception) {
      throw new LedgerPaymentClientException("Ledger request could not be serialized", exception);
    }
  }

  private static JdkClientHttpRequestFactory newRequestFactory(
      Duration connectTimeout, Duration readTimeout) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private record EventRequest(
      @JsonProperty("payment_reference") String paymentReference,
      @JsonProperty("refund_reference") @Nullable String refundReference,
      @JsonProperty("event") String event) {}

  private record CaptureRequest(
      @JsonProperty("payment_reference") String paymentReference,
      @JsonProperty("capture_reference") String captureReference,
      @JsonProperty("success") boolean success,
      @JsonProperty("amount") long amount,
      @JsonProperty("currency") String currency) {}

  private record RefundRequest(
      @JsonProperty("payment_reference") String paymentReference,
      @JsonProperty("refund_reference") String refundReference,
      @JsonProperty("net_amount") long netAmount,
      @JsonProperty("tax_amount") long taxAmount,
      @JsonProperty("currency") String currency) {}
}
