package com.outpost.backoffice.web;

import java.util.Locale;

/** Prints minor-unit amounts as the pages show them. */
final class Money {
  private Money() {}

  static String format(long minorUnits) {
    return String.format(
        Locale.ROOT,
        "%s%d.%02d",
        minorUnits < 0 ? "-" : "",
        Math.abs(minorUnits) / 100,
        Math.abs(minorUnits) % 100);
  }

  /** A balance on its side: {@code 1.25 Dr}, {@code 1.25 Cr}, or {@code 0.00}. */
  static String formatSide(long debitMinorUnits) {
    if (debitMinorUnits == 0) {
      return "0.00";
    }
    return format(Math.abs(debitMinorUnits)) + (debitMinorUnits > 0 ? " Dr" : " Cr");
  }
}
