package com.outpost.accounting.templates;

import com.outpost.account.AccountTypes.AccountType;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes.TransactionType;
import java.util.Objects;

/** Validates the source event and the registers that journal templates post against. */
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
}
