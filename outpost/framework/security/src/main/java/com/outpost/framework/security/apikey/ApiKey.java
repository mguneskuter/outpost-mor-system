package com.outpost.framework.security.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** An opaque API-key value for an authentication adapter. */
public final class ApiKey {

  private final byte[] bytes;

  private ApiKey(byte[] bytes) {
    this.bytes = bytes;
  }

  /** Creates an opaque API key from a non-empty UTF-8 value. */
  public static ApiKey fromUtf8(String value) {
    Objects.requireNonNull(value, "value");
    if (value.isEmpty()) {
      throw new IllegalArgumentException("API key must not be empty");
    }
    return new ApiKey(value.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Compares this key with another key without exposing either key's bytes.
   *
   * @param other the candidate key, or {@code null} when no key was presented
   * @return whether both keys contain the same bytes
   */
  public boolean matches(@Nullable ApiKey other) {
    return other != null && MessageDigest.isEqual(bytes, other.bytes);
  }

  @Override
  public String toString() {
    return "ApiKey[REDACTED]";
  }
}
