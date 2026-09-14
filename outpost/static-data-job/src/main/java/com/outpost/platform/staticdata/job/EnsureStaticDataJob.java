package com.outpost.platform.staticdata.job;

import java.util.List;

/** Inserts missing reference-data records and fails closed on divergence. */
public final class EnsureStaticDataJob {
  private final List<EnsureStaticDataOperator<?, ?, ?>> operators;

  /** Discovers one operator for each reference-data table. */
  public EnsureStaticDataJob(List<EnsureStaticDataOperator<?, ?, ?>> operators) {
    this.operators = List.copyOf(operators);
  }

  /** Inserts missing records and fails closed on divergence. */
  public void ensure() {
    for (EnsureStaticDataOperator<?, ?, ?> operator : operators) {
      operator.ensure();
    }
  }
}
