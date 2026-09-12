package com.outpost.gateway.report.client.ledger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.gateway.report.client.BalanceReport;
import com.outpost.gateway.report.client.LedgerReportClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** HTTP adapter for Ledger's balance report routes, signed with the Gateway key. */
public final class LedgerReportHttpClient implements LedgerReportClient {
  private static final byte[] EMPTY_BODY = new byte[0];
  private final RestClient client;
  private final HmacKey signingKey;
  private final ObjectMapper objectMapper;

  /** Creates a Ledger report client with bounded connection and response timeouts. */
  public LedgerReportHttpClient(
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
  public BalanceReport taxBalances() {
    return fetch("/v1/report/balance/tax");
  }

  @Override
  public BalanceReport merchantBalances() {
    return fetch("/v1/report/balance/merchant");
  }

  private BalanceReport fetch(String path) {
    byte[] response;
    try {
      response =
          client
              .get()
              .uri(URI.create(path))
              .header("X-Outpost-Signature", HmacSha256.sign(signingKey, EMPTY_BODY).toBase64())
              .retrieve()
              .body(byte[].class);
    } catch (RestClientResponseException exception) {
      throw new LedgerReportClientException("Ledger balance report request failed", exception);
    } catch (RuntimeException exception) {
      throw new LedgerReportClientException("Ledger balance report request failed", exception);
    }
    return toBalanceReport(deserialize(response));
  }

  private ReportResponse deserialize(byte[] body) {
    try {
      return objectMapper.readValue(body, ReportResponse.class);
    } catch (JacksonException exception) {
      throw new LedgerReportClientException(
          "Ledger balance report response could not be parsed", exception);
    }
  }

  private static BalanceReport toBalanceReport(ReportResponse response) {
    return new BalanceReport(
        response.accounts().stream()
            .map(
                account ->
                    new BalanceReport.Account(
                        account.accountCode(),
                        account.name(),
                        account.balances().stream()
                            .map(
                                balance ->
                                    new BalanceReport.Balance(balance.currency(), balance.amount()))
                            .toList()))
            .toList());
  }

  private static JdkClientHttpRequestFactory newRequestFactory(
      Duration connectTimeout, Duration readTimeout) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private record ReportResponse(@JsonProperty("accounts") List<Account> accounts) {
    private record Account(
        @JsonProperty("account_code") String accountCode,
        @JsonProperty("name") String name,
        @JsonProperty("balances") List<Balance> balances) {}

    private record Balance(
        @JsonProperty("currency") String currency, @JsonProperty("amount") long amount) {}
  }
}
