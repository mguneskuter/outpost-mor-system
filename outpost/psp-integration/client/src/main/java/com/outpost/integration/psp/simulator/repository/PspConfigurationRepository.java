package com.outpost.integration.psp.simulator.repository;

import java.util.Optional;

/** Looks up provider configuration by PSP account code. */
public interface PspConfigurationRepository {
  /** Returns the matching configuration, if it exists. */
  Optional<PspConfiguration> findByCode(String code);
}
