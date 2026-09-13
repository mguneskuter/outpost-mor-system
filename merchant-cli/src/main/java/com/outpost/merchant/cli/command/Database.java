package com.outpost.merchant.cli.command;

import org.springframework.dao.DataAccessException;

/** Prints why Outpost's database could not be read, without a stack trace. */
final class Database {
  private Database() {}

  static String unreadable(DataAccessException exception) {
    return "cannot read Outpost's database: " + exception.getMostSpecificCause().getMessage();
  }
}
