package com.outpost.pspsimulator.webhook;

import com.outpost.pspsimulator.configuration.SimulatorProperties;
import com.outpost.pspsimulator.psp.PspAccount;
import com.outpost.pspsimulator.psp.PspAccounts;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

/** Posts signed webhooks to Outpost's PSP webhook route. */
@Component
public final class WebhookDispatcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebhookDispatcher.class);
  private static final String API_KEY_HEADER = "X-Outpost-Api-Key";
  private static final String SIGNATURE_HEADER = "X-Outpost-Signature";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  private final RestClient restClient;
  private final PspAccounts pspAccounts;
  private final WebhookSigner signer;
  private final ObjectMapper objectMapper;

  /** Creates a dispatcher configured for the simulator's Outpost destination. */
  public WebhookDispatcher(
      SimulatorProperties properties, PspAccounts pspAccounts, ObjectMapper objectMapper) {
    this.pspAccounts = pspAccounts;
    this.signer = new WebhookSigner();
    this.objectMapper = objectMapper;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    restClient =
        RestClient.builder()
            .baseUrl(properties.outpostBaseUrl())
            .requestFactory(requestFactory)
            .build();
  }

  /**
   * Posts a webhook to Outpost's {@code /v1/psp/{psp-code}/webhook} route, authenticated with the
   * PSP's API key and signed with its HMAC secret. A failed delivery is logged and not retried.
   */
  public void dispatch(WebhookPayload payload) {
    PspAccount account =
        pspAccounts
            .findByCode(payload.pspCode())
            .orElseThrow(
                () -> new IllegalStateException("PSP is not configured: " + payload.pspCode()));
    byte[] body = serialize(payload);
    try {
      restClient
          .post()
          .uri("/v1/psp/{pspCode}/webhook", payload.pspCode())
          .contentType(MediaType.APPLICATION_JSON)
          .header(API_KEY_HEADER, account.apiKey())
          .header(SIGNATURE_HEADER, signer.signBase64(account.hmacSecret(), body))
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      LOGGER.warn(
          "webhook delivery failed pspCode={} eventCode={} pspReference={}",
          payload.pspCode(),
          payload.eventCode().getCode(),
          payload.pspReference(),
          exception);
    }
  }

  private byte[] serialize(WebhookPayload payload) {
    try {
      return objectMapper.writeValueAsBytes(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("could not serialise webhook payload", exception);
    }
  }
}
