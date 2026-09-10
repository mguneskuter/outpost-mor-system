package com.outpost.framework.security.hmac;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Immutable, fixed-size HMAC-SHA-256 signature that never reveals its bytes in diagnostics. */
public final class HmacSignature {

  private static final int LENGTH = 32;
  private final byte[] bytes;

  private HmacSignature(byte[] bytes) {
    this.bytes = bytes;
  }

  /** Creates a signature from exactly 32 bytes. */
  public static HmacSignature of(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (bytes.length != LENGTH) {
      throw new IllegalArgumentException("HMAC signature has invalid length");
    }
    return new HmacSignature(bytes.clone());
  }

  /** Decodes a standard Base64 signature and rejects malformed input. */
  public static HmacSignature fromBase64(String encoded) {
    Objects.requireNonNull(encoded, "encoded");
    try {
      return of(Base64.getDecoder().decode(encoded));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("HMAC signature is malformed", exception);
    }
  }

  byte[] copyBytes() {
    return bytes.clone();
  }

  /** Returns the signature in standard Base64 form for transport. */
  public String toBase64() {
    return Base64.getEncoder().encodeToString(bytes);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof HmacSignature signature
        && java.security.MessageDigest.isEqual(bytes, signature.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "HmacSignature[REDACTED]";
  }
}
