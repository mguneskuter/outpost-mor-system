package com.outpost.ledger;

import com.outpost.account.AccountTypes;
import com.outpost.accounting.AccountTypeRegisterTypes;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.common.iso.Currencies;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts the reference rows a Ledger integration test needs before the application starts. */
public final class LedgerStaticDataFixtures {

  private LedgerStaticDataFixtures() {}

  /** Inserts one row per constant of every enum the Ledger's static-data check verifies. */
  public static void materialize(JdbcTemplate jdbcTemplate) {
    for (Currencies currency : Currencies.values()) {
      var value = currency.getValue();
      jdbcTemplate.update(
          "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
          value.getCurrencyId(),
          value.getCurrencyCode(),
          value.getExponent());
    }
    for (AccountTypes type : AccountTypes.values()) {
      var value = type.getValue();
      jdbcTemplate.update(
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          value.getAccountTypeId(),
          value.getCode());
    }
    for (RegisterTypes type : RegisterTypes.values()) {
      var value = type.getValue();
      jdbcTemplate.update(
          "INSERT INTO register_type (register_type_id, register_type_code) VALUES (?, ?)",
          value.getRegisterTypeId(),
          value.getCode());
    }
    for (AccountTypeRegisterTypes mapping : AccountTypeRegisterTypes.values()) {
      var value = mapping.getValue();
      jdbcTemplate.update(
          "INSERT INTO account_type_register_type "
              + "(account_type_register_type_id, account_type_id, register_type_id) "
              + "VALUES (?, ?, ?)",
          value.getAccountTypeRegisterTypeId(),
          value.getAccountType().getAccountTypeId(),
          value.getRegisterType().getRegisterTypeId());
    }
    for (TransactionTypes type : TransactionTypes.values()) {
      var value = type.getValue();
      jdbcTemplate.update(
          "INSERT INTO transaction_type (transaction_type_id, code) VALUES (?, ?)",
          value.getTransactionTypeId(),
          value.getCode());
    }
    for (TransactionEventTypes type : TransactionEventTypes.values()) {
      var value = type.getValue();
      jdbcTemplate.update(
          "INSERT INTO transaction_event_type "
              + "(transaction_event_type_id, code, requires_journal_entry) VALUES (?, ?, ?)",
          value.getTransactionEventTypeId(),
          value.getCode(),
          value.requiresJournalEntry());
    }
    for (JournalEntryTypes type : JournalEntryTypes.values()) {
      var value = type.getValue();
      jdbcTemplate.update(
          "INSERT INTO journal_entry_type (journal_entry_type_id, code) VALUES (?, ?)",
          value.getJournalEntryTypeId(),
          value.getCode());
    }
  }

  /** Inserts one rate per ordered currency pair for {@code date}, or all pairs but one. */
  public static void insertRates(
      JdbcTemplate jdbcTemplate, LocalDate date, boolean includeAllPairs) {
    long id = date.getDayOfMonth() * 100L + 1;
    for (Currencies base : Currencies.values()) {
      for (Currencies quote : Currencies.values()) {
        if (base == quote
            || (!includeAllPairs && base == Currencies.CZK && quote == Currencies.DKK)) {
          continue;
        }
        jdbcTemplate.update(
            "INSERT INTO fx_rate "
                + "(fx_rate_id, base_currency_id, quote_currency_id, rate_date, rate, source) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
            id++,
            base.getValue().getCurrencyId(),
            quote.getValue().getCurrencyId(),
            date,
            new BigDecimal("1.2500000000"),
            "ECB");
      }
    }
  }

  /** Inserts one fee per ordered currency pair, or all pairs but one. */
  public static void insertFees(JdbcTemplate jdbcTemplate, boolean includeAllPairs) {
    long id = 1;
    for (Currencies base : Currencies.values()) {
      for (Currencies quote : Currencies.values()) {
        if (base == quote
            || (!includeAllPairs && base == Currencies.CZK && quote == Currencies.DKK)) {
          continue;
        }
        jdbcTemplate.update(
            "INSERT INTO fx_fee "
                + "(fx_fee_id, base_currency_id, quote_currency_id, fee_rate_bps) "
                + "VALUES (?, ?, ?, ?)",
            id++,
            base.getValue().getCurrencyId(),
            quote.getValue().getCurrencyId(),
            100);
      }
    }
  }
}
