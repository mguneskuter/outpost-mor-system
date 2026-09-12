package com.outpost.pspsimulator.psp;

import com.outpost.pspsimulator.configuration.SimulatorProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The simulator's configured PSP accounts, keyed by code. */
@Component
public final class PspAccounts {

  private final Map<String, PspAccount> byCode;

  /** Creates a registry from the configured PSP accounts. */
  public PspAccounts(SimulatorProperties properties) {
    Map<String, PspAccount> accounts = new LinkedHashMap<>();
    for (PspAccount account : properties.psps()) {
      PspAccount previous = accounts.putIfAbsent(account.code(), account);
      if (previous != null) {
        throw new IllegalArgumentException("duplicate PSP code: " + account.code());
      }
    }
    byCode = Map.copyOf(accounts);
  }

  /** Returns the PSP account with the exact code, if configured. */
  public Optional<PspAccount> findByCode(String code) {
    return Optional.ofNullable(byCode.get(code));
  }
}
