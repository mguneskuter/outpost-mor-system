package com.outpost.payment.order;

import com.outpost.payment.common.Amount;
import java.math.BigDecimal;

/** Calculates the tax on one order line. */
public final class LineTaxCalculator {

  /**
   * Returns the tax on a line's net amount at the line's tax rate, rounded to minor units with
   * HALF_EVEN. An order's tax is the sum of its individually rounded line taxes.
   *
   * @throws ArithmeticException when the tax does not fit in signed 64-bit minor units
   */
  public Amount calculateTax(Amount netAmount, BigDecimal taxRate) {
    return netAmount.multipliedBy(taxRate);
  }
}
