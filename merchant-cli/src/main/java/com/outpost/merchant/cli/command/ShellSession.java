package com.outpost.merchant.cli.command;

import com.outpost.merchant.cli.configuration.MerchantCliProperties.MerchantCredentials;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** The merchant the shell currently acts as, chosen among those with configured credentials. */
public final class ShellSession {
  private final Map<String, MerchantCredentials> merchants;
  private String merchantCode;

  /** Starts as the first configured merchant, by code. */
  public ShellSession(Map<String, MerchantCredentials> merchants) {
    this.merchants = new TreeMap<>(merchants);
    this.merchantCode = this.merchants.keySet().iterator().next();
  }

  /** The code of the current merchant. */
  public String merchantCode() {
    return merchantCode;
  }

  /** The current merchant's credentials. */
  public MerchantCredentials credentials() {
    return Objects.requireNonNull(merchants.get(merchantCode));
  }

  /** Whether credentials are configured for the merchant with this code. */
  public boolean hasCredentials(String code) {
    return merchants.containsKey(code);
  }

  /**
   * Switches to the merchant with this code.
   *
   * @throws IllegalArgumentException when no credentials are configured for it
   */
  public void use(String code) {
    if (!hasCredentials(code)) {
      throw new IllegalArgumentException("no credentials configured for " + code);
    }
    merchantCode = code;
  }
}
