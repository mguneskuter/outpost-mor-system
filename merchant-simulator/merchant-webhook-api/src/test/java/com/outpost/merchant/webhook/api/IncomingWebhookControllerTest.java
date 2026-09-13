package com.outpost.merchant.webhook.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    properties = {
      "merchant.webhook.secret=correct-horse-battery-staple",
      "merchant.webhook.api-key=merchant-api-key"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class IncomingWebhookControllerTest {

  private static final String SECRET = "correct-horse-battery-staple";
  private static final String CALLBACK =
      "{\"merchant_reference\":\"merchant_123\",\"outpost_reference\":\"outpost_456\","
          + "\"event_type\":\"CAPTURED\",\"event_timestamp\":\"2026-09-12T00:00:00Z\","
          + "\"success\":true,\"reason\":null}";

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @AfterAll
  static void closeClient() {
    CLIENT.close();
  }

  @Test
  void answersNoContentAndLogsNoRawCallbackContent(CapturedOutput output) throws Exception {
    HttpResponse<Void> response = post(CALLBACK, signature(SECRET, CALLBACK));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(output).doesNotContain(CALLBACK).doesNotContain(SECRET);
  }

  @Test
  void answersUnauthorizedWhenTheSignatureWasMadeWithAnotherSecret() throws Exception {
    HttpResponse<Void> response = post(CALLBACK, signature("another-secret", CALLBACK));

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void answersUnauthorizedWhenTheSignatureHeaderIsMissing() throws Exception {
    HttpResponse<Void> response = post(CALLBACK, null);

    assertThat(response.statusCode()).isEqualTo(401);
  }

  private HttpResponse<Void> post(String rawBody, @Nullable String signature) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/webhook/events"))
            .header("Content-Type", "application/json")
            .header("X-API-Key", "merchant-api-key")
            .POST(HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8));
    if (signature != null) {
      request.header("X-Outpost-Signature", signature);
    }
    return CLIENT.send(request.build(), HttpResponse.BodyHandlers.discarding());
  }

  private static String signature(String secret, String rawBody) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder()
          .encodeToString(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
