package com.outpost.framework.security.hmac;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable HMAC key that never reveals its bytes in diagnostics. */
public final class HmacKey {

  private final byte[] bytes;

  private HmacKey(byte[] bytes) {
    this.bytes = bytes;
  }

  /** Creates a key from a non-empty copy of the supplied bytes. */
  public static HmacKey of(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (bytes.length == 0) {
      throw new IllegalArgumentException("HMAC key must not be empty");
    }
    return new HmacKey(bytes.clone());
  }

  /** Creates a key from a non-empty UTF-8 secret. */
  public static HmacKey fromUtf8(String secret) {
    Objects.requireNonNull(secret, "secret");
    if (secret.isBlank()) {
      throw new IllegalArgumentException("HMAC key must not be blank");
    }
    return of(secret.getBytes(StandardCharsets.UTF_8));
  }

  byte[] copyBytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof HmacKey key && java.security.MessageDigest.isEqual(bytes, key.bytes);
  }

  @Override
  public int hashCode() {
    return 1;
  }

  @Override
  public String toString() {
    return "HmacKey[REDACTED]";
  }
}
