package com.outpost.framework.security.encryption;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Decrypts secrets stored as Base64 of a 12-byte IV followed by the AES-GCM ciphertext and tag. */
public final class AesGcmSecret {
  private static final int IV_LENGTH = 12;
  private static final int TAG_BITS = 128;
  private final SecretKeySpec key;

  /**
   * Creates a decryptor from a Base64-encoded AES key.
   *
   * @throws IllegalStateException when the key is blank, not Base64, or not 16, 24, or 32 bytes
   */
  public AesGcmSecret(String encodedKey) {
    if (encodedKey == null || encodedKey.isBlank()) {
      throw new IllegalStateException("AES key is required");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(encodedKey);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("AES key is not Base64", e);
    }
    if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
      throw new IllegalStateException("AES key must be 16, 24, or 32 bytes");
    }
    key = new SecretKeySpec(bytes, "AES");
  }

  /**
   * Decrypts one stored secret.
   *
   * @throws IllegalArgumentException when the ciphertext is malformed or fails authentication
   */
  public String decrypt(String ciphertext) {
    try {
      byte[] packed = Base64.getDecoder().decode(ciphertext);
      byte[] iv = new byte[IV_LENGTH];
      ByteBuffer buffer = ByteBuffer.wrap(packed);
      buffer.get(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = new byte[buffer.remaining()];
      buffer.get(encrypted);
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | RuntimeException e) {
      throw new IllegalArgumentException("encrypted secret cannot be decrypted", e);
    }
  }
}
