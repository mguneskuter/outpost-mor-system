package com.outpost.ledger.report.service;

import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.accounting.AccountTypeRegisterTypes;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.ReportPeriod;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.report.repository.BalanceReportRepository;
import com.outpost.ledger.report.repository.RegisterBalance;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a balance report from the register balances of a period: each balance is shown positive on
 * its register's normal side, in major units of its currency.
 */
public final class BalanceReportService {
  private final BalanceReportRepository repository;

  /** Creates a service backed by the balance report repository. */
  public BalanceReportService(BalanceReportRepository repository) {
    this.repository = repository;
  }

  /** Reports every merchant, tax authority, and platform account over the period. */
  public BalanceReport platform(ReportPeriod period) {
    return report(
        period,
        repository.findPlatformRegisterBalances(period.postedFrom(), period.postedBefore()));
  }

  /** Reports the merchant account with this code over the period. */
  public BalanceReport merchant(String merchantCode, ReportPeriod period) {
    return report(
        period,
        repository.findMerchantRegisterBalances(
            merchantCode, period.postedFrom(), period.postedBefore()));
  }

  /**
   * Walks the rows in their query order into accounts, then balance accounts, then balances.
   *
   * @throws IllegalStateException when a row names an account type, register type, currency, or
   *     account-type and register-type pair the platform does not define
   */
  private static BalanceReport report(ReportPeriod period, List<RegisterBalance> rows) {
    Map<String, Map<String, List<BalanceReport.Balance>>> accounts = new LinkedHashMap<>();
    for (RegisterBalance row : rows) {
      List<BalanceReport.Balance> balances =
          accounts
              .computeIfAbsent(row.accountCode(), accountCode -> new LinkedHashMap<>())
              .computeIfAbsent(row.registerTypeCode(), registerTypeCode -> new ArrayList<>());
      if (row.currencyCode() != null) {
        balances.add(balance(row, row.currencyCode()));
      }
    }
    List<BalanceReport.Account> reported = new ArrayList<>();
    accounts.forEach(
        (accountCode, balanceAccounts) -> {
          List<BalanceReport.BalanceAccount> reportedBalanceAccounts = new ArrayList<>();
          balanceAccounts.forEach(
              (balanceAccountCode, balances) ->
                  reportedBalanceAccounts.add(
                      new BalanceReport.BalanceAccount(balanceAccountCode, balances)));
          reported.add(new BalanceReport.Account(accountCode, reportedBalanceAccounts));
        });
    return new BalanceReport(period.from(), period.to(), reported);
  }

  private static BalanceReport.Balance balance(RegisterBalance row, String currencyCode) {
    AccountType accountType =
        AccountTypes.fromCode(row.accountTypeCode())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unknown account type code: " + row.accountTypeCode()));
    RegisterType registerType =
        RegisterTypes.fromCode(row.registerTypeCode())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unknown register type code: " + row.registerTypeCode()));
    AccountTypeRegisterTypes pair =
        AccountTypeRegisterTypes.fromAccountTypeAndRegisterType(accountType, registerType)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Register type "
                            + row.registerTypeCode()
                            + " is not permitted on account type "
                            + row.accountTypeCode()));
    Currency currency =
        Currencies.fromCurrencyCode(currencyCode)
            .orElseThrow(() -> new IllegalStateException("Unknown currency code: " + currencyCode));
    long shown =
        switch (pair.normalBalance()) {
          case DEBIT -> row.quantity();
          case CREDIT -> -row.quantity();
        };
    return new BalanceReport.Balance(
        currency.getCurrencyCode(),
        BigDecimal.valueOf(shown, currency.getExponent()).toPlainString());
  }
}
