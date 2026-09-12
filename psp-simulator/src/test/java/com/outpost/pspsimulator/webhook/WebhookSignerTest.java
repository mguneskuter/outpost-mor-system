package com.outpost.pspsimulator.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebhookSignerTest {

  private static final String SECRET = "Jefe";
  private static final byte[] PAYLOAD =
      "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8);

  @Test
  void signsTheExactPayloadWithTheKnownVector() {
    assertThat(new WebhookSigner().signBase64(SECRET, PAYLOAD))
        .isEqualTo("W9zBRr9gdU5qBCQmCJV1x1oAPwidJzmDnexYuWTsOEM=");
  }

  @Test
  void signatureChangesWithThePayload() {
    WebhookSigner signer = new WebhookSigner();
    assertThat(signer.signBase64(SECRET, "changed".getBytes(StandardCharsets.UTF_8)))
        .isNotEqualTo(signer.signBase64(SECRET, PAYLOAD));
  }
}
