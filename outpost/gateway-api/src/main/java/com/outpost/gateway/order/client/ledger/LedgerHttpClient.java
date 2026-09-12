package com.outpost.gateway.order.client.ledger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.gateway.order.client.LedgerClient;
import com.outpost.gateway.order.client.LedgerPayment;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** HTTP adapter for Ledger payment creation. */
public final class LedgerHttpClient implements LedgerClient {
  private final RestClient client;
  private final HmacKey signingKey;
  private final ObjectMapper objectMapper;

  /** Creates a Ledger client with bounded connection and response timeouts. */
  public LedgerHttpClient(
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
  public void createPayment(LedgerPayment payment) {
    byte[] body = serialize(PaymentRequest.from(payment));
    try {
      client
          .post()
          .uri(URI.create("/v1/payment"))
          .header("X-Outpost-Signature", HmacSha256.sign(signingKey, body).toBase64())
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      boolean retryable = exception.getStatusCode().value() >= 500;
      throw new LedgerClientException("Ledger payment creation failed", retryable, exception);
    } catch (RuntimeException exception) {
      throw new LedgerClientException("Ledger payment creation failed", true, exception);
    }
  }

  private byte[] serialize(PaymentRequest request) {
    try {
      return objectMapper.writeValueAsBytes(request);
    } catch (JacksonException exception) {
      throw new LedgerClientException(
          "Ledger payment request could not be serialized", false, exception);
    }
  }

  private static JdkClientHttpRequestFactory newRequestFactory(
      Duration connectTimeout, Duration readTimeout) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private record PaymentRequest(
      @JsonProperty("payment_reference") String paymentReference,
      @JsonProperty("merchant_code") String merchantCode,
      @JsonProperty("psp_code") String pspCode,
      @JsonProperty("shopper_country") String shopperCountry,
      @JsonProperty("shopper_country_subdivision") String shopperCountrySubdivision,
      @JsonProperty("net_amount") long netAmount,
      @JsonProperty("tax_amount") long taxAmount,
      @JsonProperty("gross_amount") long grossAmount,
      String currency) {
    static PaymentRequest from(LedgerPayment payment) {
      return new PaymentRequest(
          payment.paymentReference(),
          payment.merchantCode(),
          payment.pspCode(),
          payment.shopperCountry().getIsoCode(),
          payment.shopperCountrySubdivision() == null
              ? null
              : payment.shopperCountrySubdivision().getCode(),
          payment.netAmount().quantity(),
          payment.taxAmount().quantity(),
          payment.grossAmount().quantity(),
          payment.grossAmount().currency().getCurrencyCode());
    }
  }
}
