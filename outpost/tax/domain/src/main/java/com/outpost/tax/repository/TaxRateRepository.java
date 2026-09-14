package com.outpost.tax.repository;

import com.outpost.tax.TaxRate;
import java.util.Collection;

/** Repository for tax rates used by payment checkout and accounting. */
public interface TaxRateRepository {

  /** Returns the currently available tax rates. */
  Collection<TaxRate> findTaxRates();
}
