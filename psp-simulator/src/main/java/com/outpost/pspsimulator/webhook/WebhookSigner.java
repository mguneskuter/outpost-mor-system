package com.outpost.pspsimulator.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signs webhook bodies with HMAC-SHA-256 for the {@code X-Outpost-Signature} header. */
public final class WebhookSigner {

  private static final String ALGORITHM = "HmacSHA256";

  /**
   * Signs the exact raw payload bytes with the PSP's HMAC secret.
   *
   * @return the Base64-encoded signature
   */
  public String signBase64(String secret, byte[] rawPayload) {
    Objects.requireNonNull(secret, "secret");
    Objects.requireNonNull(rawPayload, "rawPayload");
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return Base64.getEncoder().encodeToString(mac.doFinal(rawPayload));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
    }
  }
}
