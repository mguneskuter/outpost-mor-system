package com.outpost.framework.security.hmac;

/** Seam for verifying a raw payload and its service-level signature. */
@FunctionalInterface
public interface SignedPayloadVerifier {

  /** Returns whether the exact raw payload has a valid service-hop signature. */
  boolean verify(byte[] rawPayload, HmacSignature signature);
}
