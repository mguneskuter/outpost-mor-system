package com.outpost.framework.security.api;

/** Seam for verifying an API key with an owning authentication adapter. */
@FunctionalInterface
public interface ApiKeyVerifier {

  /** Returns whether the opaque presented key is accepted by the owning adapter. */
  boolean verify(ApiKey presentedKey);
}
