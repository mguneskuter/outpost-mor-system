package com.outpost.pspsimulator.psp;

/** A configured PSP account the simulator stands in for. */
public record PspAccount(String code, String apiKey, String hmacSecret) {

  /** Validates the credentials required to identify and authenticate a PSP. */
  public PspAccount {
    requireNonBlank(code, "code");
    requireNonBlank(apiKey, "apiKey");
    requireNonBlank(hmacSecret, "hmacSecret");
  }

  private static void requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
