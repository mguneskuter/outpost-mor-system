package com.outpost.integration.psp.simulator.repository;

import com.outpost.integration.psp.simulator.PspConfiguration;
import java.util.Optional;

/** Looks up provider configuration by PSP account code. */
public interface PspConfigurationRepository {
  /** Finds the configuration of the PSP account with this code. */
  Optional<PspConfiguration> findPspConfigurationByPspCode(String pspCode);
}
