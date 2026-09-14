package com.outpost.backoffice.register;

import java.util.List;

/** Read-only access to the registers Outpost keeps and the lines booked on them. */
public interface RegisterBalanceRepository {
  /**
   * Lists the registers of every merchant, tax authority, PSP, and platform account in every
   * operating currency, ordered by account type, account, register, and currency; a register with
   * no line in a currency has zero debits and credits. Each filter, when not empty, keeps only the
   * rows with one of its account types, account codes, or register types.
   */
  List<RegisterBalance> findBalances(
      List<String> accountTypes, List<String> accountCodes, List<String> registerTypes);

  /** Lists the codes of the account types that hold registers, ordered by code. */
  List<String> findAccountTypeCodes();

  /**
   * Lists the codes of the accounts holding registers, of the given account types when not empty,
   * ordered by code.
   */
  List<String> findAccountCodes(List<String> accountTypes);

  /** Lists the register type codes, ordered by code. */
  List<String> findRegisterTypeCodes();
}
