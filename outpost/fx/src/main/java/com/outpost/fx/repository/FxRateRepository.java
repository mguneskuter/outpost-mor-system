package com.outpost.fx.repository;

import com.outpost.fx.FxRate;
import java.util.Collection;

/** Reads the exchange rates available to the application. */
public interface FxRateRepository {

  /** Returns all available exchange rates. */
  Collection<FxRate> findAll();
}
