package com.outpost.framework.security.encryption;

/** Port for decrypting encrypted application secrets. */
@FunctionalInterface
public interface AesGcmSecret {
  /** Decrypts a stored ciphertext, failing when it is invalid. */
  String decrypt(String ciphertext);
}
