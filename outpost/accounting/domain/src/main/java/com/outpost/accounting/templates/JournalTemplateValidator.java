package com.outpost.accounting.templates;

import com.outpost.account.AccountTypes.AccountType;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes.TransactionType;
import com.outpost.common.iso.Currencies.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validates the source event, the registers that journal templates post against, and the balance of
 * the entries they build.
 */
final class JournalTemplateValidator {

  /**
   * Requires {@code sourceEvent} to be an {@code eventType} event on a {@code transactionType}
   * transaction.
   *
   * @throws IllegalArgumentException if the event or its transaction has another type
   */
  void requireSource(
      TransactionEvent sourceEvent,
      TransactionEventType eventType,
      TransactionType transactionType) {
    Objects.requireNonNull(sourceEvent, "sourceEvent");
    if (!sourceEvent.getTransactionEventType().equals(eventType)
        || !sourceEvent.getTransaction().getTransactionType().equals(transactionType)) {
      throw new IllegalArgumentException(
          "source event must be "
              + eventType.getCode()
              + " on a "
              + transactionType.getCode()
              + " transaction");
    }
  }

  /**
   * Requires {@code register} to have {@code registerType} and to be held by an account of {@code
   * accountType}.
   *
   * @throws IllegalArgumentException if the register type or the account type differs
   */
  void requireRegister(Register register, RegisterType registerType, AccountType accountType) {
    Objects.requireNonNull(register, "register");
    if (!register.getRegisterType().equals(registerType)
        || !register.getAccount().getAccountType().equals(accountType)) {
      throw new IllegalArgumentException(
          "expected a "
              + registerType.getCode()
              + " register of a "
              + accountType.getCode()
              + " account: "
              + register.getRegisterType().getCode()
              + " register of a "
              + register.getAccount().getAccountType().getCode()
              + " account");
    }
  }

  /**
   * Requires {@code register} to be held by the merchant account of the source event's transaction.
   *
   * @throws IllegalArgumentException if another account holds the register
   */
  void requireMerchantRegister(Register register, TransactionEvent sourceEvent) {
    if (!register.getAccount().equals(sourceEvent.getTransaction().getMerchantAccount())) {
      throw new IllegalArgumentException(
          register.getRegisterType().getCode()
              + " register must belong to the source transaction's merchant account");
    }
  }

  /**
   * Requires the lines of {@code entry} to sum to zero in each currency.
   *
   * @throws IllegalArgumentException if the lines in any currency do not sum to zero
   */
  void requireBalanced(JournalEntry entry) {
    Objects.requireNonNull(entry, "entry");
    Map<Currency, Long> sumByCurrency = new LinkedHashMap<>();
    for (JournalEntryLine line : entry.getJournalEntryLines()) {
      sumByCurrency.merge(line.getAmount().currency(), line.getAmount().quantity(), Math::addExact);
    }
    for (Map.Entry<Currency, Long> sum : sumByCurrency.entrySet()) {
      if (sum.getValue() != 0) {
        throw new IllegalArgumentException(
            "journal entry lines in "
                + sum.getKey().getCurrencyCode()
                + " sum to "
                + sum.getValue()
                + " instead of zero");
      }
    }
  }
}
