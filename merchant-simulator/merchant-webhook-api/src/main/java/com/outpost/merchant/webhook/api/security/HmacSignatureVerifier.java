package com.outpost.merchant.webhook.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HmacSignatureVerifier {
  private static final Logger log = LoggerFactory.getLogger(HmacSignatureVerifier.class);
  private final byte[] secret;

  public HmacSignatureVerifier(String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public boolean isValid(@Nullable String signature, byte[] body) {
    if (signature == null) {
      log.warn("Webhook HMAC verification failed: signature missing");
      return false;
    }
    try {
      byte[] provided = Base64.getDecoder().decode(signature);
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return MessageDigest.isEqual(mac.doFinal(body), provided);
    } catch (Exception exception) {
      log.warn("Webhook HMAC verification failed: malformed signature");
      return false;
    }
  }
}
