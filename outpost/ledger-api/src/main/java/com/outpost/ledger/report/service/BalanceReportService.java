package com.outpost.ledger.report.service;

import com.outpost.ledger.report.repository.BalanceLine;
import com.outpost.ledger.report.repository.BalanceReportRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the tax-authority and merchant balance read models. */
public final class BalanceReportService {
  private final BalanceReportRepository repository;

  /** Creates a service backed by the balance report repository. */
  public BalanceReportService(BalanceReportRepository repository) {
    this.repository = repository;
  }

  /** Returns what is owed to tax authorities, grouped by account and currency. */
  public BalanceReport tax() {
    return report(repository.findTaxBalances());
  }

  /** Returns what is owed to merchants, grouped by account and currency. */
  public BalanceReport merchant() {
    return report(repository.findMerchantBalances());
  }

  private static BalanceReport report(List<BalanceLine> lines) {
    Map<String, AccountBuilder> accounts = new LinkedHashMap<>();
    for (BalanceLine line : lines) {
      accounts
          .computeIfAbsent(
              line.accountCode(),
              ignored -> new AccountBuilder(line.accountCode(), line.accountName()))
          .balances
          .add(new BalanceReport.Balance(line.currency(), line.amount()));
    }
    return new BalanceReport(accounts.values().stream().map(AccountBuilder::build).toList());
  }

  private static final class AccountBuilder {
    private final String accountCode;
    private final String name;
    private final List<BalanceReport.Balance> balances = new ArrayList<>();

    private AccountBuilder(String accountCode, String name) {
      this.accountCode = accountCode;
      this.name = name;
    }

    private BalanceReport.Account build() {
      return new BalanceReport.Account(accountCode, name, balances);
    }
  }
}
