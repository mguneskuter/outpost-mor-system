package com.outpost.merchant.cli.command;

import java.util.Locale;

/** Prints minor-unit amounts as the shell shows them. */
final class Money {
  private Money() {}

  static String format(long minorUnits, String currency) {
    return String.format(
        Locale.ROOT, "%d.%02d %s", minorUnits / 100, Math.abs(minorUnits % 100), currency);
  }
}
