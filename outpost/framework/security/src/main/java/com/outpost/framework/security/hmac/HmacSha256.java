package com.outpost.framework.security.hmac;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/** Java-standard-library HMAC-SHA-256 signing and constant-time verification. */
public final class HmacSha256 {

  private static final String ALGORITHM = "HmacSHA256";

  private HmacSha256() {}

  /** Signs the exact raw payload bytes. */
  public static HmacSignature sign(HmacKey key, byte[] rawPayload) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(rawPayload, "rawPayload");
    return HmacSignature.of(mac(key, rawPayload));
  }

  /** Convenience overload for UTF-8 payloads; callers with raw bytes must use the byte[] method. */
  public static HmacSignature signUtf8(HmacKey key, String rawPayload) {
    Objects.requireNonNull(rawPayload, "rawPayload");
    return sign(key, rawPayload.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Verifies a signature using constant-time comparison.
   *
   * <p>Malformed or absent signatures return {@code false}; secret material never appears in the
   * returned error or exception.
   */
  public static boolean verify(
      @Nullable HmacKey key, byte @Nullable [] rawPayload, @Nullable HmacSignature signature) {
    if (key == null || rawPayload == null || signature == null) {
      return false;
    }
    return MessageDigest.isEqual(mac(key, rawPayload), signature.copyBytes());
  }

  private static byte[] mac(HmacKey key, byte[] rawPayload) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key.copyBytes(), ALGORITHM));
      return mac.doFinal(rawPayload);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
    }
  }
}
