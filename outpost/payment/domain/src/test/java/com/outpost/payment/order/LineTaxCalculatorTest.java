package com.outpost.payment.order;

import static com.outpost.payment.order.OrderFixtures.eur;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LineTaxCalculatorTest {
  private final LineTaxCalculator calculator = new LineTaxCalculator();

  @ParameterizedTest
  @CsvSource({"100, 0.19, 19", "6, 0.25, 2", "10, 0.25, 2", "14, 0.25, 4"})
  void roundsTheLineTaxToMinorUnitsHalfEven(long net, String rate, long expectedTax) {
    assertEquals(eur(expectedTax), calculator.calculateTax(eur(net), new BigDecimal(rate)));
  }
}
