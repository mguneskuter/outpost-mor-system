package com.outpost.fx.repository;

import com.outpost.fx.FxFee;
import java.util.Collection;

/** Reads the platform fees available to the application. */
public interface FxFeeRepository {

  /** Returns all available platform fees. */
  Collection<FxFee> findAll();
}
